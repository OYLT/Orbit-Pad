use crate::{Core, Session};
use serde_json::{json, Value};
use std::io::{ErrorKind, Read};
use std::net::{SocketAddr, TcpStream};
use std::sync::{Arc, Mutex};
use std::thread::sleep;
use std::time::{Duration, Instant};
use tungstenite::{client::IntoClientRequest, protocol::WebSocketConfig, Message, WebSocket};

fn connection_error(session: &Session) -> String {
    match session.method.as_str() {
        "usb" => "USB 通道未就绪，请在电脑端开启 USB 连接并确认手机授权",
        "bluetooth" => "蓝牙连接失败，请确认电脑服务已开启且双方已在系统中配对",
        _ => "无法连接电脑接收端，请检查 Wi-Fi 和防火墙",
    }
    .into()
}

fn pair(session: &Session) -> Result<String, String> {
    let agent = ureq::AgentBuilder::new()
        .redirects(0)
        .timeout(Duration::from_secs(if session.method == "bluetooth" {
            15
        } else {
            5
        }))
        .build();
    let status = agent
        .get(&(session.address.origin() + "/api/status"))
        .call()
        .map_err(|_| connection_error(session))?;
    let status: Value =
        serde_json::from_reader(status.into_reader().take(16384)).map_err(|_| "接收端响应异常")?;
    if status["app"] != "orbit-pad" {
        return Err("该地址不是 Orbit Pad 接收端".into());
    }
    let mut code = session.code.clone();
    if code.is_empty() && ["usb", "bluetooth"].contains(&session.method.as_str()) {
        let response = agent
            .get(&(session.address.origin() + "/api/direct/bootstrap"))
            .call()
            .map_err(|_| "请更新电脑接收端并开启对应的直连服务")?;
        let data: Value = serde_json::from_reader(response.into_reader().take(16384))
            .map_err(|_| "直连握手格式异常")?;
        code = data["code"].as_str().unwrap_or("").into();
        if code.len() != 6 || !code.bytes().all(|c| c.is_ascii_digit()) {
            return Err("直连握手失败".into());
        }
    }
    let response = match agent
        .post(&(session.address.origin() + "/api/pair"))
        .send_json(json!({"code":code,"device":format!("Android · {} · {}", env!("CARGO_PKG_VERSION"), session.method)}))
    {
        Ok(r) => r,
        Err(ureq::Error::Status(_, response)) => {
            let data: Value =
                serde_json::from_reader(response.into_reader().take(16384)).unwrap_or(Value::Null);
            return Err(data["error"].as_str().unwrap_or("配对失败").into());
        }
        Err(_) => return Err(connection_error(session)),
    };
    let data: Value =
        serde_json::from_reader(response.into_reader().take(16384)).map_err(|_| "配对响应异常")?;
    let token = data["token"].as_str().unwrap_or("");
    if token.len() != 64
        || !token
            .bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
    {
        return Err("接收端配对协议异常".into());
    }
    Ok(token.into())
}
fn open(session: &Session) -> Result<WebSocket<TcpStream>, String> {
    let socket =
        TcpStream::connect_timeout(&SocketAddr::V4(session.address.0), Duration::from_secs(3))
            .map_err(|_| "无法连接电脑接收端")?;
    socket.set_nodelay(true).map_err(|e| e.to_string())?;
    let handshake_timeout = Duration::from_secs(if session.method == "bluetooth" { 15 } else { 3 });
    socket
        .set_read_timeout(Some(handshake_timeout))
        .map_err(|e| e.to_string())?;
    socket
        .set_write_timeout(Some(handshake_timeout))
        .map_err(|e| e.to_string())?;
    let mut request = format!("ws://{}/ws", session.address.0)
        .into_client_request()
        .map_err(|e| e.to_string())?;
    request.headers_mut().insert(
        "Origin",
        session
            .address
            .origin()
            .parse()
            .map_err(|_| "地址格式异常")?,
    );
    let config = WebSocketConfig {
        max_message_size: Some(16384),
        max_frame_size: Some(16384),
        ..WebSocketConfig::default()
    };
    let (mut ws, _) = tungstenite::client::client_with_config(request, socket, Some(config))
        .map_err(|_| "接收端握手失败，请重试")?;
    ws.get_mut()
        .set_nonblocking(true)
        .map_err(|e| e.to_string())?;
    send(&mut ws, json!({"type":"auth","token":session.token}))?;
    Ok(ws)
}
fn send(ws: &mut WebSocket<TcpStream>, value: Value) -> Result<(), String> {
    match ws.send(Message::Text(value.to_string())) {
        Ok(()) => Ok(()),
        Err(tungstenite::Error::Io(e)) if e.kind() == ErrorKind::WouldBlock => Ok(()),
        Err(e) => Err(e.to_string()),
    }
}

enum Outcome {
    Cancelled,
    Retry(String),
    Terminal(String),
}
fn connected(core: &Arc<Mutex<Core>>, mut ws: WebSocket<TcpStream>, epoch: u64) -> Outcome {
    let started = Instant::now();
    let mut last_ping = Instant::now() - Duration::from_secs(1);
    let mut last_received = Instant::now();
    let mut authenticated = false;
    let mut revision = u64::MAX;
    let mut last_state = Instant::now() - Duration::from_secs(1);
    loop {
        let (current, settings, rev, pending) = {
            let mut c = core.lock().unwrap();
            let current = c.current(epoch);
            let pending = if authenticated && last_state.elapsed() >= Duration::from_millis(16) {
                last_state = Instant::now();
                c.pending.drain(..).map(|p| p.state).collect::<Vec<_>>()
            } else {
                Vec::new()
            };
            (current, c.settings.clone(), c.revision, pending)
        };
        if !current {
            let _ = send(&mut ws, json!({"type":"release"}));
            let _ = ws.close(None);
            let _ = ws.flush();
            return Outcome::Cancelled;
        }
        if authenticated {
            if revision != rev {
                if send(&mut ws, json!({"type":"release"})).is_err()
                    || send(
                        &mut ws,
                        json!({"type":"mapping","mapping":settings.mapping}),
                    )
                    .is_err()
                    || send(
                        &mut ws,
                        json!({"type":"deadzone","value":settings.deadzone}),
                    )
                    .is_err()
                {
                    return Outcome::Retry("发送设置失败".into());
                }
                revision = rev;
            }
            // Preserve quick down/up transitions; never replace a pending release with a later hold.
            for state in pending {
                if send(
                    &mut ws,
                    json!({"type":"state","buttons":state.buttons,"axes":state.axes,"triggers":state.triggers}),
                )
                .is_err()
                {
                    return Outcome::Retry("连接中断".into());
                }
            }
            if last_ping.elapsed() >= Duration::from_millis(500) {
                if send(
                    &mut ws,
                    json!({"type":"ping","sent":started.elapsed().as_millis() as u64}),
                )
                .is_err()
                {
                    return Outcome::Retry("连接中断".into());
                }
                last_ping = Instant::now();
            }
        }
        if let Err(e) = ws.flush() {
            if !matches!(&e,tungstenite::Error::Io(io) if io.kind()==ErrorKind::WouldBlock) {
                return Outcome::Retry("连接中断".into());
            }
        }
        // Bounded work lets cancellation and releases run even if a peer floods status frames.
        for _ in 0..32 {
            match ws.read() {
                Ok(Message::Text(text)) => {
                    let data: Value = match serde_json::from_str(&text) {
                        Ok(data) => data,
                        Err(_) => return Outcome::Terminal("接收端协议异常".into()),
                    };
                    last_received = Instant::now();
                    let mut c = core.lock().unwrap();
                    if !c.current(epoch) {
                        break;
                    }
                    match data["type"].as_str().unwrap_or("") {
                        "authenticated" | "status" => {
                            if data["type"] == "authenticated" {
                                authenticated = true;
                                c.connected = true;
                                c.release();
                            }
                            if !authenticated {
                                return Outcome::Terminal("接收端认证顺序异常".into());
                            }
                            let ready = data["ready"].as_bool().unwrap_or(false);
                            let paused = data["paused"].as_bool().unwrap_or(false);
                            if ready != c.ready || paused != c.paused {
                                c.release();
                            }
                            c.ready = ready;
                            c.paused = paused;
                            c.message = if paused {
                                "电脑已暂停接收"
                            } else if !ready {
                                "等待电脑输入桥接就绪"
                            } else {
                                "已连接"
                            }
                            .into();
                        }
                        "pong" => {
                            if let Some(sent) = data["sent"].as_u64() {
                                c.rtt = Some(
                                    (started.elapsed().as_millis() as u64).saturating_sub(sent),
                                );
                            }
                        }
                        "busy" => {
                            return Outcome::Terminal("已有手柄连接，请先断开另一台手机".into())
                        }
                        "revoked" => {
                            return Outcome::Terminal("配对已撤销，请输入新的配对码".into())
                        }
                        _ => return Outcome::Terminal("接收端协议异常".into()),
                    }
                }
                Ok(Message::Close(frame)) => {
                    if let Some(frame) = frame {
                        let code: u16 = frame.code.into();
                        if [4001, 4002, 4008, 4009].contains(&code) {
                            return Outcome::Terminal(if frame.reason.is_empty() {
                                "请重新配对".into()
                            } else {
                                frame.reason.to_string()
                            });
                        }
                    }
                    return Outcome::Retry("连接已关闭".into());
                }
                Ok(Message::Ping(_)) | Ok(Message::Pong(_)) => {
                    last_received = Instant::now();
                }
                Ok(_) => return Outcome::Terminal("接收端协议异常".into()),
                Err(tungstenite::Error::Io(e)) if e.kind() == ErrorKind::WouldBlock => break,
                Err(_) => return Outcome::Retry("连接中断".into()),
            }
        }
        if last_received.elapsed() > Duration::from_secs(3) {
            return Outcome::Retry("接收端响应超时".into());
        }
        sleep(Duration::from_millis(8));
    }
}

pub(super) fn worker(core: Arc<Mutex<Core>>) {
    let mut retry_at = Instant::now();
    let mut retry_epoch = 0;
    let mut failures = 0u32;
    loop {
        let desired = {
            let c = core.lock().unwrap();
            if c.foreground {
                c.session.clone().map(|s| (s, c.epoch))
            } else {
                None
            }
        };
        let Some((mut session, epoch)) = desired else {
            sleep(Duration::from_millis(20));
            continue;
        };
        if epoch != retry_epoch {
            retry_at = Instant::now();
            retry_epoch = epoch;
            failures = 0;
        }
        if Instant::now() < retry_at {
            sleep(Duration::from_millis(20));
            continue;
        }
        if session.token.is_none() {
            match pair(&session) {
                Ok(token) => {
                    let mut c = core.lock().unwrap();
                    if !c.current(epoch) {
                        continue;
                    }
                    session.token = Some(token.clone());
                    if let Some(s) = c.session.as_mut() {
                        s.token = Some(token);
                    }
                }
                Err(error) => {
                    let mut c = core.lock().unwrap();
                    if c.current(epoch) {
                        c.message = error;
                        c.session = None;
                        c.release();
                    }
                    continue;
                }
            }
        }
        if !core.lock().unwrap().current(epoch) {
            continue;
        }
        let outcome = match open(&session) {
            Ok(ws) => connected(&core, ws, epoch),
            Err(error) => Outcome::Retry(error),
        };
        let mut c = core.lock().unwrap();
        if !c.current(epoch) {
            continue;
        }
        c.connected = false;
        c.ready = false;
        c.rtt = None;
        c.release();
        match outcome {
            Outcome::Cancelled => {}
            Outcome::Terminal(error) => {
                c.message = error;
                c.session = None;
            }
            Outcome::Retry(error) => {
                c.message = format!("{error}，正在重连…");
                failures = failures.saturating_add(1);
                retry_at =
                    Instant::now() + Duration::from_millis((250u64 << failures.min(4)).min(4000));
            }
        }
    }
}
