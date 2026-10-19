pub mod model;
mod transport;

use model::{Address, Input, InputState, Settings};
use serde_json::{json, Value};
use std::collections::VecDeque;
use std::sync::{Arc, Mutex, OnceLock};

#[derive(Clone)]
struct Session {
    address: Address,
    code: String,
    token: Option<String>,
    method: String,
}
struct Pending {
    state: InputState,
    edge: bool,
}
struct Core {
    session: Option<Session>,
    epoch: u64,
    foreground: bool,
    focused: bool,
    settings_open: bool,
    connected: bool,
    ready: bool,
    paused: bool,
    message: String,
    rtt: Option<u64>,
    input: Input,
    settings: Settings,
    revision: u64,
    pending: VecDeque<Pending>,
}
impl Default for Core {
    fn default() -> Self {
        Self {
            session: None,
            epoch: 0,
            foreground: true,
            focused: true,
            settings_open: false,
            connected: false,
            ready: false,
            paused: false,
            message: "在电脑接收端查看地址和配对码".into(),
            rtt: None,
            input: Input::default(),
            settings: Settings::default(),
            revision: 0,
            pending: VecDeque::new(),
        }
    }
}
impl Core {
    fn enabled(&self) -> bool {
        self.foreground
            && self.focused
            && !self.settings_open
            && self.connected
            && self.ready
            && !self.paused
    }
    fn release(&mut self) {
        self.input.clear();
        self.pending.clear();
        self.pending.push_back(Pending {
            state: InputState::default(),
            edge: true,
        });
    }
    fn current(&self, epoch: u64) -> bool {
        self.epoch == epoch && self.foreground && self.session.is_some()
    }
    fn snapshot(&self) -> Value {
        json!({ "connected": self.connected, "ready": self.ready, "paused": self.paused,
            "enabled": self.enabled(), "message": self.message, "rtt": self.rtt,
            "settings": self.settings, "state": self.input.state(), "version": env!("CARGO_PKG_VERSION"),
            "reconnectable": self.session.is_some(), "settingsOpen": self.settings_open })
    }
}

/// All Android calls are short, in-memory operations. Only the Rust worker touches sockets.
pub struct Engine {
    core: Arc<Mutex<Core>>,
}
impl Default for Engine {
    fn default() -> Self {
        Self::new()
    }
}
impl Engine {
    pub fn new() -> Self {
        let core = Arc::new(Mutex::new(Core::default()));
        let worker = core.clone();
        std::thread::Builder::new()
            .name("orbit-network".into())
            .spawn(move || transport::worker(worker))
            .expect("network worker");
        Self { core }
    }
    pub fn call(&self, command: Value) -> Result<Value, String> {
        let mut c = self.core.lock().map_err(|_| "手柄核心状态异常")?;
        match command["op"].as_str().unwrap_or("") {
            "snapshot" => {}
            "layout" => return Ok(model::layout(&c.settings)),
            "parse_qr" => {
                return serde_json::to_value(model::Pairing::parse(
                    command["value"].as_str().unwrap_or(""),
                )?)
                .map_err(|e| e.to_string())
            }
            "preset" => {
                return serde_json::to_value(Settings::preset(
                    command["name"].as_str().unwrap_or(""),
                )?)
                .map_err(|e| e.to_string())
            }
            "connect" => {
                let address = Address::parse(command["address"].as_str().unwrap_or(""))?;
                let code = command["code"].as_str().unwrap_or("");
                let method = command["method"].as_str().unwrap_or("manual");
                if !["scan", "manual", "usb", "bluetooth"].contains(&method) {
                    return Err("连接方式无效".into());
                }
                let direct = ["usb", "bluetooth"].contains(&method) && address.0.ip().is_loopback();
                if !(direct && code.is_empty())
                    && (code.len() != 6 || !code.bytes().all(|c| c.is_ascii_digit()))
                {
                    return Err("请输入电脑上显示的六位配对码".into());
                }
                c.epoch += 1;
                c.release();
                c.connected = false;
                c.ready = false;
                c.rtt = None;
                c.session = Some(Session {
                    address,
                    code: code.into(),
                    token: None,
                    method: method.into(),
                });
                c.message = "正在连接…".into();
            }
            "disconnect" => {
                c.epoch += 1;
                c.release();
                c.session = None;
                c.connected = false;
                c.ready = false;
                c.settings_open = false;
                c.rtt = None;
                c.message = "已断开连接".into();
            }
            "pause" => {
                c.foreground = false;
                c.release();
                c.epoch += 1;
                c.connected = false;
                c.ready = false;
            }
            "resume" => {
                c.foreground = true;
            }
            "focus" => {
                c.focused = command["value"].as_bool().unwrap_or(false);
                if !c.focused {
                    c.release();
                }
            }
            "settings" => {
                c.settings_open = command["open"].as_bool().unwrap_or(false);
                c.release();
            }
            "release" => c.release(),
            "configure" => {
                let settings: Settings = serde_json::from_value(command["settings"].clone())
                    .map_err(|_| "设置格式无效")?;
                settings.validate()?;
                c.release();
                if settings.mapping != c.settings.mapping
                    || settings.deadzone != c.settings.deadzone
                {
                    c.revision += 1;
                }
                c.settings = settings;
            }
            "touch" => {
                if c.enabled() {
                    if c.settings
                        .layout
                        .get(command["control"].as_str().unwrap_or(""))
                        .is_some_and(|p| p.hidden)
                    {
                        return Ok(c.snapshot());
                    }
                    let id = command["id"]
                        .as_i64()
                        .and_then(|n| i32::try_from(n).ok())
                        .ok_or("触点编号无效")?;
                    c.input.touch(
                        id,
                        command["phase"].as_str().unwrap_or(""),
                        command["control"].as_str().unwrap_or(""),
                        command["x"].as_f64().unwrap_or(f64::NAN) as f32,
                        command["y"].as_f64().unwrap_or(f64::NAN) as f32,
                    )?;
                    let state = c.input.state();
                    if !c.pending.back().is_some_and(|p| p.state == state) {
                        if c.pending.len() >= 128 {
                            c.release();
                            c.epoch += 1;
                            c.connected = false;
                        } else {
                            let edge = command["phase"] != "move";
                            if !edge && c.pending.back().is_some_and(|p| !p.edge) {
                                c.pending.pop_back();
                            }
                            c.pending.push_back(Pending { state, edge });
                        }
                    }
                }
            }
            _ => return Err("未知核心指令".into()),
        }
        Ok(c.snapshot())
    }
}

static ENGINE: OnceLock<Engine> = OnceLock::new();
pub fn dispatch(command: &str) -> String {
    let result = std::panic::catch_unwind(|| {
        let value = serde_json::from_str(command).map_err(|_| "指令格式无效".to_string())?;
        ENGINE.get_or_init(Engine::new).call(value)
    });
    match result {
        Ok(Ok(value)) => value.to_string(),
        Ok(Err(error)) => json!({"error":error}).to_string(),
        Err(_) => json!({"error":"手柄核心异常，请重启应用"}).to_string(),
    }
}

#[cfg(target_os = "android")]
#[no_mangle]
pub extern "system" fn Java_app_orbitpad_controller_NativeBridge_call(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    command: jni::objects::JString,
) -> jni::sys::jstring {
    let input: String = match env.get_string(&command) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    match env.new_string(dispatch(&input)) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn lifecycle_clears_held_input_and_ignores_background_touches() {
        let e = Engine::new();
        {
            let mut c = e.core.lock().unwrap();
            c.connected = true;
            c.ready = true;
        }
        let down = json!({"op":"touch","id":1,"phase":"down","control":"a","x":0,"y":0});
        assert_eq!(
            e.call(down.clone()).unwrap()["state"]["buttons"],
            json!(["a"])
        );
        assert_eq!(
            e.call(json!({"op":"pause"})).unwrap()["state"]["buttons"],
            json!([])
        );
        assert_eq!(e.call(down).unwrap()["state"]["buttons"], json!([]));
    }
    #[test]
    fn settings_and_focus_release_before_reconfiguring() {
        let e = Engine::new();
        {
            let mut c = e.core.lock().unwrap();
            c.connected = true;
            c.ready = true;
        }
        e.call(json!({"op":"touch","id":0,"phase":"down","control":"move","x":1,"y":-1}))
            .unwrap();
        assert_eq!(
            e.call(json!({"op":"settings","open":true})).unwrap()["state"]["axes"],
            json!([0.0, 0.0, 0.0, 0.0])
        );
        assert!(!e.call(json!({"op":"snapshot"})).unwrap()["enabled"]
            .as_bool()
            .unwrap());
        assert!(e
            .call(json!({"op":"configure","settings":{"deadzone":0.9}}))
            .is_err());
    }
}
