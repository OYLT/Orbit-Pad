use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::{BTreeMap, BTreeSet};
use std::net::{Ipv4Addr, SocketAddrV4};

pub const BUTTONS: [&str; 15] = [
    "a", "b", "x", "y", "lb", "rb", "lt", "rt", "select", "start", "xbox", "up", "down", "left",
    "right",
];
pub const CONTROLS: [&str; 23] = [
    "a",
    "b",
    "x",
    "y",
    "lb",
    "rb",
    "lt",
    "rt",
    "select",
    "start",
    "xbox",
    "up",
    "down",
    "left",
    "right",
    "moveUp",
    "moveDown",
    "moveLeft",
    "moveRight",
    "lookUp",
    "lookDown",
    "lookLeft",
    "lookRight",
];
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Address(pub SocketAddrV4);
impl Address {
    pub fn parse(raw: &str) -> Result<Self, String> {
        let value = raw.trim();
        let value = value.strip_prefix("http://").unwrap_or(value);
        let value = value.strip_suffix('/').unwrap_or(value);
        let (host, port) = match value.split_once(':') {
            Some((host, port)) => (
                host,
                port.parse::<u16>().map_err(|_| "端口必须在 1–65535 之间")?,
            ),
            None => (value, 3210),
        };
        let ip = host
            .parse::<Ipv4Addr>()
            .map_err(|_| "请输入电脑的 IPv4 地址，例如 192.168.1.100:3210")?;
        let [a, b, _, _] = ip.octets();
        let lan = a == 10
            || a == 127
            || (a == 192 && b == 168)
            || (a == 172 && (16..=31).contains(&b))
            || (a == 169 && b == 254)
            || (a == 198 && (b == 18 || b == 19));
        if !lan {
            return Err("请使用同一 Wi-Fi 内的电脑地址".into());
        }
        if port == 0 {
            return Err("端口必须在 1–65535 之间".into());
        }
        Ok(Self(SocketAddrV4::new(ip, port)))
    }
    pub fn origin(&self) -> String {
        format!("http://{}", self.0)
    }
}

#[derive(Debug, Serialize)]
pub struct Pairing {
    pub address: String,
    pub code: String,
}
impl Pairing {
    /// Only accept the receiver's own QR format; scanning never opens arbitrary URLs.
    pub fn parse(raw: &str) -> Result<Self, String> {
        if raw.len() > 2048 {
            return Err("二维码内容过长".into());
        }
        let value = raw
            .trim()
            .strip_prefix("http://")
            .ok_or("请扫描 Orbit Pad 电脑接收端的二维码")?;
        let (host, fragment) = value
            .split_once("/controller#pair=")
            .ok_or("请扫描 Orbit Pad 电脑接收端的二维码")?;
        if fragment.len() != 6 || !fragment.bytes().all(|c| c.is_ascii_digit()) {
            return Err("二维码配对码无效，请刷新电脑接收端二维码".into());
        }
        let address = Address::parse(host)?;
        Ok(Self {
            address: address.0.to_string(),
            code: fragment.into(),
        })
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct Placement {
    pub x: Option<f32>,
    pub y: Option<f32>,
    pub scale: f32,
    pub hidden: bool,
    pub width: f32,
    pub height: f32,
}
impl Default for Placement {
    fn default() -> Self {
        Self {
            x: None,
            y: None,
            scale: 1.0,
            hidden: false,
            width: 1.0,
            height: 1.0,
        }
    }
}

fn legacy_key(key: &str) -> bool {
    (key.len() == 1
        && key
            .bytes()
            .all(|c| c.is_ascii_uppercase() || c.is_ascii_digit()))
        || [
            "Space",
            "Enter",
            "Escape",
            "Tab",
            "Shift",
            "Control",
            "Alt",
            "ArrowUp",
            "ArrowDown",
            "ArrowLeft",
            "ArrowRight",
        ]
        .contains(&key)
}

pub fn valid_key(key: &str) -> bool {
    CONTROLS.contains(&key) || ["l3", "r3", "none"].contains(&key)
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct Settings {
    pub mapping: BTreeMap<String, String>,
    pub deadzone: f32,
    pub size: u16,
    pub haptics: bool,
    pub preset: String,
    pub layout: BTreeMap<String, Placement>,
    pub linear_triggers: bool,
    pub gyro_enabled: bool,
    pub gyro_range: f32,
    pub gyro_deadzone: f32,
    pub gyro_invert: bool,
}
impl Default for Settings {
    fn default() -> Self {
        Self {
            mapping: CONTROLS
                .into_iter()
                .zip(CONTROLS)
                .map(|(a, b)| (a.into(), b.into()))
                .collect(),
            deadzone: 0.24,
            size: 100,
            haptics: true,
            preset: "general".into(),
            layout: BTreeMap::new(),
            linear_triggers: false,
            gyro_enabled: false,
            gyro_range: 45.0,
            gyro_deadzone: 2.0,
            gyro_invert: false,
        }
    }
}
impl Settings {
    // Preserve saved layouts and feel settings when upgrading keyboard templates.
    pub fn migrate_keyboard_mapping(&mut self) {
        // Old templates may still carry the removed manual recenter control.
        self.layout.remove("calibrate");
        let old = CONTROLS
            .iter()
            .filter(|c| **c != "xbox")
            .all(|c| self.mapping.get(*c).is_some_and(|k| legacy_key(k)));
        if old && [CONTROLS.len(), CONTROLS.len() - 1].contains(&self.mapping.len()) {
            self.mapping = Self::default().mapping;
            self.preset = "general".into();
        } else if self.mapping.len() == CONTROLS.len() - 1 && !self.mapping.contains_key("xbox") {
            self.mapping.insert("xbox".into(), "xbox".into());
        }
    }
    pub fn validate(&self) -> Result<(), String> {
        if self.mapping.len() != CONTROLS.len()
            || !CONTROLS
                .iter()
                .all(|c| self.mapping.get(*c).is_some_and(|k| valid_key(k)))
        {
            return Err("按键映射无效".into());
        }
        if !self.deadzone.is_finite()
            || !(0.1..=0.5).contains(&self.deadzone)
            || !(80..=110).contains(&self.size)
            || !["general", "racing", "platform", "custom"].contains(&self.preset.as_str())
        {
            return Err("手感设置无效".into());
        }
        if !self.gyro_range.is_finite()
            || !(15.0..=90.0).contains(&self.gyro_range)
            || !self.gyro_deadzone.is_finite()
            || !(0.0..=10.0).contains(&self.gyro_deadzone)
        {
            return Err("陀螺仪设置无效".into());
        }
        for (id, p) in &self.layout {
            if !(BUTTONS.contains(&id.as_str()) || ["move", "look"].contains(&id.as_str()))
                || !p.width.is_finite()
                || !(0.6..=3.0).contains(&p.width)
                || !p.height.is_finite()
                || !(0.6..=4.0).contains(&p.height)
                || (!["lb", "lt", "rb", "rt"].contains(&id.as_str())
                    && (p.width != 1.0 || p.height != 1.0))
                || !p.scale.is_finite()
                || !(0.6..=1.6).contains(&p.scale)
                || p.x
                    .is_some_and(|v| !v.is_finite() || !(0.0..=1000.0).contains(&v))
                || p.y
                    .is_some_and(|v| !v.is_finite() || !(0.0..=450.0).contains(&v))
            {
                return Err("控件布局无效".into());
            }
        }
        Ok(())
    }
    pub fn preset(name: &str) -> Result<Self, String> {
        let mut settings = Self::default();
        let changes: &[(&str, &str)] = match name {
            "general" => &[],
            "racing" | "platform" => &[],
            _ => return Err("未知预设".into()),
        };
        for (control, key) in changes {
            settings.mapping.insert((*control).into(), (*key).into());
        }
        settings.preset = name.into();
        Ok(settings)
    }
}

#[derive(Clone, Default, Debug, PartialEq, Serialize)]
pub struct InputState {
    pub buttons: BTreeSet<String>,
    pub axes: [f32; 4],
    pub triggers: [f32; 2],
}
#[derive(Clone)]
struct Touch {
    control: String,
    x: f32,
    y: f32,
}
#[derive(Default)]
pub struct Input {
    touches: BTreeMap<i32, Touch>,
    pub linear_triggers: bool,
    pub trigger_horizontal: [bool; 2],
    pub gyro_x: f32,
}
impl Input {
    pub fn clear(&mut self) {
        self.touches.clear();
        self.gyro_x = 0.0;
    }
    pub fn touch(
        &mut self,
        id: i32,
        phase: &str,
        control: &str,
        x: f32,
        y: f32,
    ) -> Result<(), String> {
        if phase == "cancel" {
            self.clear();
            return Ok(());
        }
        if phase == "up" {
            self.touches.remove(&id);
            return Ok(());
        }
        if !x.is_finite()
            || !y.is_finite()
            || !(BUTTONS.contains(&control) || ["move", "look"].contains(&control))
        {
            return Err("无效触摸输入".into());
        }
        match phase {
            "down" => {
                if !self.touches.contains_key(&id) && self.touches.len() < 16 {
                    // Each stick belongs to its first finger until that finger lifts.
                    if ["move", "look"].contains(&control)
                        && self.touches.values().any(|t| t.control == control)
                    {
                        return Ok(());
                    }
                    self.touches.insert(
                        id,
                        Touch {
                            control: control.into(),
                            x,
                            y,
                        },
                    );
                }
            }
            "move" => {
                if let Some(touch) = self.touches.get_mut(&id) {
                    touch.x = x;
                    touch.y = y;
                }
            }
            _ => return Err("无效触摸事件".into()),
        }
        Ok(())
    }
    pub fn state(&self) -> InputState {
        let mut state = InputState::default();
        state.axes[0] = self.gyro_x;
        for touch in self.touches.values() {
            if touch.control == "move" || touch.control == "look" {
                let radius = (touch.x * touch.x + touch.y * touch.y).sqrt().max(1.0);
                let index = if touch.control == "move" { 0 } else { 2 };
                state.axes[index] = touch.x / radius;
                state.axes[index + 1] = touch.y / radius;
            } else if touch.control == "lt" || touch.control == "rt" {
                let index = usize::from(touch.control == "rt");
                // Press starts at full travel; dragging down reduces pressure.
                let value = if self.linear_triggers {
                    ((if self.trigger_horizontal[index] {
                        touch.x
                    } else {
                        touch.y
                    }) + 1.0)
                        / 2.0
                } else {
                    1.0
                }
                .clamp(0.0, 1.0);
                state.triggers[index] = state.triggers[index].max(value);
                state.buttons.insert(touch.control.clone());
            } else {
                state.buttons.insert(touch.control.clone());
            }
        }
        state
    }
}

pub fn layout(settings: &Settings) -> Value {
    let scale = f32::from(settings.size) / 100.0;
    let controls = [
        ("lb", "LB", "LB", 90., 92., 132., 50.),
        ("lt", "LT", "LT 模拟扳机", 242., 92., 132., 50.),
        ("rb", "RB", "RB", 910., 92., 132., 50.),
        ("rt", "RT", "RT 模拟扳机", 758., 92., 132., 50.),
        ("move", "L", "左摇杆，模拟量输出", 154., 237., 172., 172.),
        ("look", "R", "右摇杆，模拟量输出", 650., 331., 150., 150.),
        ("up", "↑", "方向上", 345., 273., 52., 52.),
        ("down", "↓", "方向下", 345., 381., 52., 52.),
        ("left", "←", "方向左", 291., 327., 52., 52.),
        ("right", "→", "方向右", 399., 327., 52., 52.),
        ("a", "A", "A", 858., 325., 66., 66.),
        ("b", "B", "B", 926., 257., 66., 66.),
        ("x", "X", "X", 790., 257., 66., 66.),
        ("y", "Y", "Y", 858., 189., 66., 66.),
        ("select", "View", "View 背包", 442., 188., 90., 46.),
        ("start", "Menu", "Menu 暂停", 558., 188., 90., 46.),
        ("xbox", "Xbox", "Xbox · 呼出 Game Bar", 500., 104., 54., 54.),
        ("settings", "设置", "手柄设置", 935., 29., 86., 40.),
    ];
    json!(controls
        .into_iter()
        .map(|(id, label, description, x, y, w, h)| {
            let p = settings.layout.get(id).cloned().unwrap_or_default();
            let w = (w * scale * p.scale * p.width).min(980.0);
            let h = (h * scale * p.scale * p.height).min(430.0);
            json!({
                "id": id, "label": label, "description": description,
                "x": p.x.unwrap_or(x).clamp(w/2.0+4.0, 996.0-w/2.0),
                "y": p.y.unwrap_or(y).clamp(h/2.0+4.0, 446.0-h/2.0),
                "width": w, "height": h, "visible": !p.hidden
            })
        })
        .collect::<Vec<_>>())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn saved_recenter_control_is_removed_without_losing_other_settings() {
        let mut settings = Settings {
            gyro_enabled: true,
            gyro_range: 60.0,
            ..Settings::default()
        };
        settings
            .layout
            .insert("calibrate".into(), Placement::default());
        settings.layout.insert(
            "a".into(),
            Placement {
                hidden: true,
                ..Placement::default()
            },
        );
        let mut loaded: Settings = serde_json::from_value(json!(settings)).unwrap();
        loaded.migrate_keyboard_mapping();
        loaded.validate().unwrap();
        assert!(!loaded.layout.contains_key("calibrate"));
        assert!(loaded.layout["a"].hidden);
        assert!(loaded.gyro_enabled);
        assert_eq!(loaded.gyro_range, 60.0);
        assert!(layout(&loaded)
            .as_array()
            .unwrap()
            .iter()
            .all(|p| p["id"] != "calibrate"));
    }
    #[test]
    fn old_native_templates_and_rectangular_pedals() {
        let mut s = Settings::default();
        s.mapping.remove("xbox");
        s.mapping.insert("a".into(), "y".into());
        s.migrate_keyboard_mapping();
        s.validate().unwrap();
        assert_eq!(s.mapping["a"], "y");
        assert_eq!(s.mapping["xbox"], "xbox");
        s.layout.insert(
            "lt".into(),
            Placement {
                width: 0.6,
                height: 4.0,
                ..Placement::default()
            },
        );
        s.validate().unwrap();
        let controls = layout(&s);
        let lt = controls
            .as_array()
            .unwrap()
            .iter()
            .find(|p| p["id"] == "lt")
            .unwrap();
        assert!(lt["height"].as_f64().unwrap() > lt["width"].as_f64().unwrap());
        let mut input = Input {
            linear_triggers: true,
            trigger_horizontal: [false, true],
            ..Input::default()
        };
        input.touch(0, "down", "lt", 1.0, -1.0).unwrap();
        assert_eq!(input.state().triggers[0], 0.0);
        input.touch(0, "move", "lt", -1.0, 0.5).unwrap();
        assert_eq!(input.state().triggers[0], 0.75);
        input.touch(1, "down", "rt", -0.5, 1.0).unwrap();
        assert_eq!(input.state().triggers, [0.75, 0.25]);
        input.touch(0, "up", "lt", 0.0, 0.0).unwrap();
        assert_eq!(input.state().triggers, [0.0, 0.25]);
        input.clear();
        input.linear_triggers = false;
        input.touch(0, "down", "lt", -1.0, -1.0).unwrap();
        assert_eq!(input.state().triggers[0], 1.0);
    }
    #[test]
    fn keyboard_settings_migrate_without_losing_layout_or_feel() {
        let mut settings = Settings {
            mapping: CONTROLS
                .into_iter()
                .map(|c| (c.into(), "Space".into()))
                .collect(),
            deadzone: 0.31,
            size: 90,
            ..Settings::default()
        };
        settings.layout.insert(
            "a".into(),
            Placement {
                hidden: true,
                ..Placement::default()
            },
        );
        settings.migrate_keyboard_mapping();
        settings.validate().unwrap();
        assert_eq!(settings.mapping, Settings::default().mapping);
        assert_eq!(settings.deadzone, 0.31);
        assert_eq!(settings.size, 90);
        assert!(settings.layout["a"].hidden);
    }
    #[test]
    fn triggers_support_continuous_travel_and_independent_release() {
        let mut input = Input {
            linear_triggers: true,
            ..Input::default()
        };
        input.touch(0, "down", "lt", 0., 1.).unwrap();
        input.touch(1, "down", "rt", 0., 1.).unwrap();
        assert_eq!(input.state().triggers, [1., 1.]);
        input.touch(0, "move", "lt", 0., 0.).unwrap();
        assert_eq!(input.state().triggers, [0.5, 1.]);
        input.touch(0, "up", "lt", 0., 1.).unwrap();
        assert_eq!(input.state().triggers, [0., 1.]);
        input.clear();
        assert_eq!(input.state(), InputState::default());
    }
    #[test]
    fn qr_codes_only_accept_receiver_pairing_links() {
        let pair = Pairing::parse("http://192.168.1.5:3211/controller#pair=123456").unwrap();
        assert_eq!(pair.address, "192.168.1.5:3211");
        assert_eq!(pair.code, "123456");
        for value in [
            "https://example.com/controller#pair=123456",
            "http://8.8.8.8/controller#pair=123456",
            "http://user@10.0.0.1/controller#pair=123456",
            "http://10.0.0.1/controller#pair=123456&evil=1",
            "http://10.0.0.1/controller#token=abcd",
            "javascript:alert(1)",
        ] {
            assert!(Pairing::parse(value).is_err(), "{value}");
        }
    }
    #[test]
    fn customized_layout_stays_on_canvas_and_round_trips() {
        let mut settings = Settings::default();
        settings.layout.insert(
            "a".into(),
            Placement {
                x: Some(0.),
                y: Some(450.),
                scale: 1.6,
                hidden: true,
                ..Placement::default()
            },
        );
        settings.validate().unwrap();
        let controls = layout(&settings);
        let a = controls
            .as_array()
            .unwrap()
            .iter()
            .find(|p| p["id"] == "a")
            .unwrap();
        assert!(!a["visible"].as_bool().unwrap());
        assert!(a["x"].as_f64().unwrap() > 50.);
        assert!(a["y"].as_f64().unwrap() < 400.);
        let decoded: Settings =
            serde_json::from_str(&serde_json::to_string(&settings).unwrap()).unwrap();
        decoded.validate().unwrap();
        settings
            .layout
            .insert("settings".into(), Placement::default());
        assert!(settings.validate().is_err());
    }
    #[test]
    fn addresses_are_private_ipv4_origins() {
        for (raw, expected) in [
            ("192.168.1.2", "http://192.168.1.2:3210"),
            (" http://10.0.0.2:88/ ", "http://10.0.0.2:88"),
            ("127.0.0.1:1234", "http://127.0.0.1:1234"),
        ] {
            assert_eq!(Address::parse(raw).unwrap().origin(), expected);
        }
        for raw in [
            "https://10.0.0.1",
            "8.8.8.8",
            "192.168.01.1",
            "10.0.0.1:0",
            "10.0.0.1:65536",
            "10.0.0.1/path",
            "user@10.0.0.1",
            "10.0.0.1?x=1",
            "localhost",
            "[::1]",
            "10.0.0.1\r\nX: x",
        ] {
            assert!(Address::parse(raw).is_err(), "{raw}");
        }
    }
    #[test]
    fn multi_touch_ownership_and_release() {
        let mut input = Input::default();
        input.touch(0, "down", "move", 0.8, -0.8).unwrap();
        input.touch(1, "down", "look", -0.8, -0.8).unwrap();
        input.touch(2, "down", "a", 0., 0.).unwrap();
        input.touch(3, "down", "a", 0., 0.).unwrap();
        input.touch(4, "down", "move", -1., 0.).unwrap();
        let state = input.state();
        assert!(state.axes[0] > 0.7 && state.axes[2] < -0.7);
        input.touch(2, "up", "a", 0., 0.).unwrap();
        assert!(input.state().buttons.contains("a"));
        input.touch(3, "up", "a", 0., 0.).unwrap();
        assert!(input.state().buttons.is_empty());
        input.touch(0, "up", "move", 0., 0.).unwrap();
        assert_eq!(input.state().axes[0..2], [0., 0.]);
        assert!(input.state().axes[2] < -0.7);
        input.touch(1, "cancel", "look", 0., 0.).unwrap();
        assert_eq!(input.state(), InputState::default());
    }
    #[test]
    fn settings_match_receiver_contract() {
        for name in ["general", "racing", "platform"] {
            Settings::preset(name).unwrap().validate().unwrap();
        }
        let mut s = Settings::default();
        s.mapping.insert("a".into(), "F24".into());
        assert!(s.validate().is_err());
        s = Settings::default();
        s.deadzone = f32::NAN;
        assert!(s.validate().is_err());
        s = Settings::default();
        s.mapping.remove("moveUp");
        assert!(s.validate().is_err());
    }
    #[test]
    fn invalid_touches_never_enter_state() {
        let mut input = Input::default();
        assert!(input.touch(0, "down", "a", f32::NAN, 0.).is_err());
        assert!(input.touch(0, "down", "unknown", 0., 0.).is_err());
        input.touch(0, "move", "a", 1., 1.).unwrap();
        assert_eq!(input.state(), InputState::default());
    }
}
