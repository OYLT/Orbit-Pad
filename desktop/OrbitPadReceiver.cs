using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

[assembly: System.Reflection.AssemblyTitle("Orbit Pad Receiver")]
[assembly: System.Reflection.AssemblyDescription("Phone-to-PC Wi-Fi controller receiver")]
[assembly: System.Reflection.AssemblyVersion("0.3.0.0")]

internal sealed class ReceiverService : IDisposable {
    private readonly HttpClient http;
    private readonly string root = AppDomain.CurrentDomain.BaseDirectory;
    private readonly string ownerToken = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N");
    private readonly JavaScriptSerializer json = new JavaScriptSerializer();
    private readonly object logLock = new object();
    private Process process;
    private string logTail = "";
    public int Port { get; private set; }
    public bool Running { get { return process != null && !process.HasExited; } }
    public string BaseUrl { get { return "http://127.0.0.1:" + Port; } }
    public string BrowserUrl { get { return "http://localhost:" + Port; } }

    public ReceiverService() {
        http = new HttpClient(new HttpClientHandler { UseProxy = false });
        http.Timeout = TimeSpan.FromMilliseconds(1800);
    }
    private void Log(string line) {
        if (String.IsNullOrWhiteSpace(line)) return;
        lock (logLock) { logTail = (logTail + "\n" + line); if (logTail.Length > 6000) logTail = logTail.Substring(logTail.Length - 6000); }
    }
    public async Task Start() {
        if (Running) return;
        string runtime = Path.Combine(root, "runtime", "node.exe");
        string server = Path.Combine(root, "app", "server", "index.js");
        if (!File.Exists(runtime) || !File.Exists(server)) throw new Exception("软件文件不完整。请先完整解压 ZIP，再运行 OrbitPadReceiver.exe。");
        if (process != null) { process.Dispose(); process = null; }
        Port = 0;
        for (int candidate = 3210; candidate <= 3230; candidate++) {
            try { var probe = new TcpListener(IPAddress.Any, candidate); probe.Start(); probe.Stop(); Port = candidate; break; }
            catch (SocketException) { }
        }
        if (Port == 0) throw new Exception("3210–3230 端口均被占用，请关闭其他接收端后重试。");
        var start = new ProcessStartInfo(runtime, "\"" + server + "\"") {
            WorkingDirectory = Path.Combine(root, "app"), UseShellExecute = false,
            CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8, StandardErrorEncoding = Encoding.UTF8
        };
        start.EnvironmentVariables["PORT"] = Port.ToString();
        start.EnvironmentVariables["ORBIT_OWNER_TOKEN"] = ownerToken;
        start.EnvironmentVariables["ORBIT_PARENT_PID"] = Process.GetCurrentProcess().Id.ToString();
        process = new Process { StartInfo = start, EnableRaisingEvents = true };
        process.OutputDataReceived += (sender, args) => Log(args.Data);
        process.ErrorDataReceived += (sender, args) => Log(args.Data);
        process.Start(); process.BeginOutputReadLine(); process.BeginErrorReadLine();
        for (int attempt = 0; attempt < 80; attempt++) {
            if (!Running) throw new Exception("接收进程退出：" + logTail);
            try {
                var state = await Status();
                if (Convert.ToBoolean(state["ready"])) return;
                string error = Convert.ToString(state["error"]);
                if (error.Length > 0) throw new InvalidOperationException(error);
            } catch (HttpRequestException) { } catch (TaskCanceledException) { }
            await Task.Delay(150);
        }
        throw new Exception("接收端启动超时。请检查 PowerShell 是否可用，并重试。" + logTail);
    }
    public async Task<Dictionary<string, object>> Status() {
        string raw = await http.GetStringAsync(BaseUrl + "/api/host");
        var status = json.Deserialize<Dictionary<string, object>>(raw);
        if (Convert.ToString(status["app"]) != "orbit-pad") throw new Exception("端口上的服务不是 Orbit Pad。");
        return status;
    }
    public async Task Command(string name, object body) {
        var content = new StringContent(json.Serialize(body), Encoding.UTF8, "application/json");
        using (var request = new HttpRequestMessage(HttpMethod.Post, BaseUrl + "/api/host/" + name)) {
            request.Content = content;
            if (name == "shutdown") request.Headers.Add("X-Orbit-Owner", ownerToken);
            using (var response = await http.SendAsync(request)) response.EnsureSuccessStatusCode();
        }
    }
    public async Task<byte[]> Qr(string ip) {
        return await http.GetByteArrayAsync(BaseUrl + "/api/host/qr?format=png&ip=" + Uri.EscapeDataString(ip));
    }
    public async Task Stop() {
        if (process == null) return;
        try { if (Running) await Command("shutdown", new { }); } catch { }
        var owned = process;
        await Task.Run(() => {
            try { if (!owned.HasExited && !owned.WaitForExit(4500)) owned.Kill(); } catch { }
        });
        process.Dispose(); process = null; Port = 0;
    }
    public void Dispose() { http.Dispose(); }
}

internal sealed class Card : Panel {
    public float CornerRadius = 28;
    public Card() { DoubleBuffered = true; BackColor = Color.FromArgb(29, 27, 32); Padding = new Padding(24); }
    internal static GraphicsPath Shape(RectangleF bounds, float radius) {
        float d = Math.Min(radius * 2, Math.Min(bounds.Width, bounds.Height));
        var path = new GraphicsPath();
        path.AddArc(bounds.X, bounds.Y, d, d, 180, 90);
        path.AddArc(bounds.Right - d, bounds.Y, d, d, 270, 90);
        path.AddArc(bounds.Right - d, bounds.Bottom - d, d, d, 0, 90);
        path.AddArc(bounds.X, bounds.Bottom - d, d, d, 90, 90); path.CloseFigure(); return path;
    }
    protected override void OnPaintBackground(PaintEventArgs e) {
        e.Graphics.Clear(Parent == null ? Color.FromArgb(20, 18, 24) : Parent.BackColor);
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
        using (var shape = Shape(new RectangleF(0, 0, Width - 1, Height - 1), CornerRadius * DeviceDpi / 96f))
        using (var fill = new SolidBrush(BackColor)) e.Graphics.FillPath(fill, shape);
    }
    protected override void OnPaint(PaintEventArgs e) {
        base.OnPaint(e);
    }
}

internal sealed class MaterialButton : Button {
    private bool hover, pressed;
    public MaterialButton() { SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true); }
    protected override void OnMouseEnter(EventArgs e) { hover = true; Invalidate(); base.OnMouseEnter(e); }
    protected override void OnMouseLeave(EventArgs e) { hover = pressed = false; Invalidate(); base.OnMouseLeave(e); }
    protected override void OnMouseDown(MouseEventArgs e) { pressed = true; Invalidate(); base.OnMouseDown(e); }
    protected override void OnMouseUp(MouseEventArgs e) { pressed = false; Invalidate(); base.OnMouseUp(e); }
    protected override void OnGotFocus(EventArgs e) { Invalidate(); base.OnGotFocus(e); }
    protected override void OnLostFocus(EventArgs e) { Invalidate(); base.OnLostFocus(e); }
    protected override void OnPaint(PaintEventArgs e) {
        e.Graphics.Clear(Parent == null ? Color.FromArgb(20, 18, 24) : Parent.BackColor);
        e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
        using (var shape = Card.Shape(new RectangleF(1, 1, Width - 3, Height - 3), Height / 2f)) {
            using (var fill = new SolidBrush(Enabled ? BackColor : Color.FromArgb(54, 52, 59))) e.Graphics.FillPath(fill, shape);
            if (Enabled && (hover || pressed)) using (var state = new SolidBrush(Color.FromArgb(pressed ? 35 : 20, ForeColor))) e.Graphics.FillPath(state, shape);
            if (Focused && ShowFocusCues) using (var pen = new Pen(Color.FromArgb(208, 188, 255), 2)) e.Graphics.DrawPath(pen, shape);
        }
        TextRenderer.DrawText(e.Graphics, Text, Font, ClientRectangle, Enabled ? ForeColor : Color.FromArgb(147, 143, 153), TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
    }
}

internal sealed class ReceiverForm : Form {
    [DllImport("user32.dll")] private static extern uint GetDpiForWindow(IntPtr window);
    [DllImport("dwmapi.dll")] private static extern int DwmSetWindowAttribute(IntPtr window, int attribute, ref int value, int size);
    private static readonly Color Mint = Color.FromArgb(208, 188, 255);
    private static readonly Color TextColor = Color.FromArgb(230, 224, 233);
    private static readonly Color Muted = Color.FromArgb(202, 196, 208);
    private readonly ReceiverService service = new ReceiverService();
    private readonly System.Windows.Forms.Timer polling = new System.Windows.Forms.Timer { Interval = 650 };
    private readonly Label statusLabel = new Label();
    private readonly Label pairCode = new Label();
    private readonly Label deviceLabel = new Label();
    private readonly Label keysLabel = new Label();
    private readonly Label modeLabel = new Label();
    private readonly ComboBox networks = new ComboBox();
    private readonly PictureBox qr = new PictureBox();
    private readonly Button power = new MaterialButton();
    private readonly Button pause = new MaterialButton();
    private readonly Button reset = new MaterialButton();
    private readonly NotifyIcon tray = new NotifyIcon();
    private bool busy;
    private bool paused;
    private bool closing;
    private string qrIdentity = "";
    private string codeValue = "";
    private string[] networkValues = new string[0];

    private Label Label(string text, float size, Color color, bool bold = false) {
        return new Label { Text = text, AutoSize = true, ForeColor = color, BackColor = Color.Transparent,
            Font = new Font("Microsoft YaHei UI", size, bold ? FontStyle.Bold : FontStyle.Regular), Margin = new Padding(0, 0, 0, 12) };
    }
    private Button ConfigureButton(Button button, string text, bool primary = false) {
        button.Text = text; button.Height = 42; button.Dock = DockStyle.Fill;
        button.FlatStyle = FlatStyle.Flat; button.FlatAppearance.BorderColor = Color.FromArgb(65, 84, 52);
        button.BackColor = primary ? Mint : Color.FromArgb(74, 68, 88);
        button.ForeColor = primary ? Color.FromArgb(56, 30, 114) : Color.FromArgb(232, 222, 248);
        button.Font = new Font("Microsoft YaHei UI", 10); button.Cursor = Cursors.Hand;
        button.Margin = new Padding(0, 0, 10, 0); return button;
    }
    private static void Cell(TableLayoutPanel table, Control child, int row, int span = 1) {
        table.Controls.Add(child, 0, row); if (span > 1) table.SetColumnSpan(child, span);
    }

    public ReceiverForm() {
        SuspendLayout();
        AutoScaleMode = AutoScaleMode.None;
        Text = "Orbit Pad Receiver · 电脑接收端"; Width = 1010; Height = 748;
        MinimumSize = new Size(920, 740); StartPosition = FormStartPosition.CenterScreen;
        BackColor = Color.FromArgb(20, 18, 24); ForeColor = TextColor;
        Font = new Font("Microsoft YaHei UI", 10); Padding = new Padding(28);
        Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
        var root = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 2, RowCount = 4, BackColor = BackColor };
        root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 61)); root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 39));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 95)); root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 72)); root.RowStyles.Add(new RowStyle(SizeType.Absolute, 38));
        var heading = new FlowLayoutPanel { FlowDirection = FlowDirection.TopDown, Dock = DockStyle.Fill, WrapContents = false };
        heading.Controls.Add(Label("Orbit Pad", 24, Mint));
        heading.Controls.Add(Label("手机无线手柄 · Windows 接收端 0.3.0", 10, Muted));
        Cell(root, heading, 0, 2);

        var connection = new Card { Dock = DockStyle.Fill, Margin = new Padding(0, 0, 20, 0) };
        var left = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 1, RowCount = 11 };
        left.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        float[] heights = { 42, 44, 30, 44, 30, 64, 30, 34, 26, 40, 0 };
        for (int i = 0; i < heights.Length; i++) left.RowStyles.Add(new RowStyle(i == 10 ? SizeType.Percent : SizeType.Absolute, i == 10 ? 100 : heights[i]));
        Cell(left, Label("连接手机，开始游戏", 20, TextColor), 0);
        Cell(left, Label("在 Android App 输入下方地址与配对码，\n然后把电脑切换到游戏窗口。", 10, Muted), 1);
        Cell(left, Label("电脑局域网地址", 10, Muted), 2);
        networks.DropDownStyle = ComboBoxStyle.DropDownList; networks.Dock = DockStyle.Top;
        networks.Font = new Font("Consolas", 14); networks.BackColor = Color.FromArgb(54, 52, 59); networks.ForeColor = TextColor;
        networks.FlatStyle = FlatStyle.Flat; networks.DrawMode = DrawMode.OwnerDrawFixed; networks.ItemHeight = 25;
        networks.DrawItem += (sender, args) => {
            if (args.Index < 0) return;
            Color fill = (args.State & DrawItemState.Selected) != 0 ? Color.FromArgb(74, 68, 88) : networks.BackColor;
            using (var brush = new SolidBrush(fill)) args.Graphics.FillRectangle(brush, args.Bounds);
            TextRenderer.DrawText(args.Graphics, networks.Items[args.Index].ToString(), networks.Font, args.Bounds, TextColor, TextFormatFlags.Left | TextFormatFlags.VerticalCenter);
        };
        var addressField = new Card { Dock = DockStyle.Fill, CornerRadius = 12, BackColor = networks.BackColor, Padding = new Padding(12, 4, 8, 4), Margin = new Padding(0, 0, 0, 4) };
        networks.Dock = DockStyle.Fill; addressField.Controls.Add(networks); Cell(left, addressField, 3);
        Cell(left, Label("六位配对码", 10, Muted), 4);
        pairCode.Font = new Font("Consolas", 36, FontStyle.Bold); pairCode.ForeColor = Mint; pairCode.AutoSize = true;
        pairCode.Text = "— — — — — —"; Cell(left, pairCode, 5);
        statusLabel.AutoSize = true; statusLabel.ForeColor = Muted; statusLabel.Text = "● 正在启动接收端…"; Cell(left, statusLabel, 6);
        deviceLabel.AutoSize = true; deviceLabel.ForeColor = TextColor; deviceLabel.Text = "等待手机连接"; Cell(left, deviceLabel, 7);
        Cell(left, Label("实时键盘输入", 9, Muted), 8);
        keysLabel.Dock = DockStyle.Fill; keysLabel.ForeColor = Mint; keysLabel.Font = new Font("Consolas", 12);
        keysLabel.Text = "等待操作"; keysLabel.AutoEllipsis = true; Cell(left, keysLabel, 9);
        connection.Controls.Add(left); root.Controls.Add(connection, 0, 1);

        var scan = new Card { Dock = DockStyle.Fill, Margin = new Padding(0), BackColor = Color.FromArgb(43, 41, 48) };
        var right = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 1, RowCount = 5 };
        right.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 34)); right.RowStyles.Add(new RowStyle(SizeType.Absolute, 188));
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 44)); right.RowStyles.Add(new RowStyle(SizeType.Absolute, 30));
        right.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        Cell(right, Label("扫码体验 / 手机 App", 14, TextColor, true), 0);
        qr.SizeMode = PictureBoxSizeMode.Zoom; qr.Dock = DockStyle.Fill; qr.BackColor = Color.White;
        qr.Margin = new Padding(0, 0, 0, 8); Cell(right, qr, 1);
        Cell(right, Label("二维码打开浏览器版。\nAPK 内直接输入左侧地址和配对码。", 9, Muted), 2);
        Cell(right, Label("连接小贴士", 11, Mint, true), 3);
        Cell(right, Label("01  手机与电脑连接同一个 Wi-Fi\n02  防火墙允许专用网络连接\n03  手机横屏，开始游戏", 9, Muted), 4);
        scan.Controls.Add(right); root.Controls.Add(scan, 1, 1);

        var actions = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 5, RowCount = 1, Padding = new Padding(0, 18, 0, 8) };
        for (int i = 0; i < 5; i++) actions.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 20));
        actions.Controls.Add(ConfigureButton(power, "停止接收", true)); actions.Controls.Add(ConfigureButton(pause, "暂停输入"));
        actions.Controls.Add(ConfigureButton(reset, "重置配对"));
        var copy = ConfigureButton(new MaterialButton(), "复制地址"); actions.Controls.Add(copy);
        var console = ConfigureButton(new MaterialButton(), "高级控制台"); actions.Controls.Add(console); Cell(root, actions, 2, 2);
        modeLabel.AutoSize = true; modeLabel.ForeColor = Muted; modeLabel.Font = new Font("Microsoft YaHei UI", 8);
        modeLabel.Text = "键盘映射模式 · 自带运行环境 · 最小化保留后台，关闭窗口停止接收"; Cell(root, modeLabel, 3, 2);
        Controls.Add(root);

        networks.SelectedIndexChanged += async (sender, args) => { try { await UpdateQr(); } catch { } };
        copy.Click += (sender, args) => { if (networks.SelectedItem != null) { Clipboard.SetText(networks.SelectedItem.ToString()); statusLabel.Text = "● 连接地址已复制"; } };
        console.Click += (sender, args) => { if (service.Running) Process.Start(service.BrowserUrl); };
        power.Click += async (sender, args) => {
            if (busy) return; busy = true; power.Enabled = false;
            try {
                if (service.Running) { await service.Stop(); pairCode.Text = "— — — — — —"; statusLabel.Text = "● 已停止，所有按键已释放"; deviceLabel.Text = "接收端已关闭"; keysLabel.Text = "等待操作"; }
                else { await service.Start(); await RefreshState(); }
            } catch (Exception error) { ShowError(error); }
            finally { busy = false; power.Enabled = true; power.Text = service.Running ? "停止接收" : "启动接收"; }
        };
        pause.Click += async (sender, args) => { try { if (service.Running) { await service.Command("pause", new { paused = !paused }); await RefreshState(); } } catch (Exception error) { ShowError(error); } };
        reset.Click += async (sender, args) => { try { if (service.Running) { await service.Command("reset", new { }); await RefreshState(); } } catch (Exception error) { ShowError(error); } };
        polling.Tick += async (sender, args) => {
            if (busy || closing) return;
            if (!service.Running) { power.Text = "启动接收"; pause.Enabled = reset.Enabled = false; return; }
            busy = true;
            try { await RefreshState(); } catch (Exception error) { statusLabel.Text = "● 连接暂时中断：" + error.Message; statusLabel.ForeColor = Color.SandyBrown; }
            finally { busy = false; }
        };
        var menu = new ContextMenuStrip(); menu.Items.Add("显示接收端", null, (sender, args) => Restore());
        menu.Items.Add("退出并停止接收", null, (sender, args) => Close());
        tray.Icon = Icon; tray.Text = "Orbit Pad Receiver"; tray.ContextMenuStrip = menu;
        tray.DoubleClick += (sender, args) => Restore();
        Resize += (sender, args) => { if (WindowState == FormWindowState.Minimized) { tray.Visible = true; Hide(); } };
        Shown += async (sender, args) => { busy = true; try { await service.Start(); await RefreshState(); } catch (Exception error) { if (!closing) ShowError(error); } finally { busy = false; if (!closing) polling.Start(); } };
        FormClosing += async (sender, args) => {
            if (closing) return;
            args.Cancel = true; closing = true; polling.Stop(); Enabled = false; tray.Visible = false;
            await service.Stop(); service.Dispose(); tray.Dispose(); polling.Dispose();
            Close();
        };
        ResumeLayout(true);
        float dpiScale = GetDpiForWindow(Handle) / 96f;
        if (dpiScale > 1.01f) Scale(new SizeF(dpiScale, dpiScale));
        networks.ItemHeight = networks.Font.Height + 8;
        var area = Screen.FromHandle(Handle).WorkingArea;
        MinimumSize = new Size(Math.Min(MinimumSize.Width, area.Width - 32), Math.Min(MinimumSize.Height, area.Height - 32));
        Size = new Size(Math.Min(Width, area.Width - 32), Math.Min(Height, area.Height - 32));
        StartPosition = FormStartPosition.Manual;
        Location = new Point(area.Left + (area.Width - Width) / 2, area.Top + (area.Height - Height) / 2);
        int dark = 1, caption = 0x181214, captionText = 0xe9e0e6;
        DwmSetWindowAttribute(Handle, 20, ref dark, 4);
        DwmSetWindowAttribute(Handle, 35, ref caption, 4);
        DwmSetWindowAttribute(Handle, 36, ref captionText, 4);
        ActiveControl = power;
    }
    private void Restore() { Show(); WindowState = FormWindowState.Normal; Activate(); tray.Visible = false; }
    private void ShowError(Exception error) { statusLabel.Text = "● " + error.Message; statusLabel.ForeColor = Color.SandyBrown; power.Text = service.Running ? "停止接收" : "启动接收"; }
    private async Task UpdateQr() {
        if (!service.Running || networks.SelectedItem == null) return;
        string address = networks.SelectedItem.ToString();
        string identity = address + "/" + codeValue;
        if (qrIdentity == identity) return;
        var bytes = await service.Qr(new Uri("http://" + address).Host);
        if (closing || IsDisposed) return;
        using (var stream = new MemoryStream(bytes)) using (var image = Image.FromStream(stream)) {
            var previous = qr.Image; qr.Image = new Bitmap(image); if (previous != null) previous.Dispose();
        }
        qrIdentity = identity;
    }
    private async Task RefreshState() {
        var state = await service.Status();
        if (closing || IsDisposed) return;
        paused = Convert.ToBoolean(state["paused"]);
        bool ready = Convert.ToBoolean(state["ready"]), connected = Convert.ToBoolean(state["connected"]);
        string error = Convert.ToString(state["error"]);
        codeValue = Convert.ToString(state["pairCode"]);
        pairCode.Text = String.Join(" ", codeValue.ToCharArray());
        var urls = ((System.Collections.IEnumerable)state["urls"]).Cast<object>().ToArray(); var values = new List<string>();
        foreach (var item in urls) values.Add(Convert.ToString(item).Replace("http://", ""));
        string valueKey = String.Join(",", values), oldKey = String.Join(",", networkValues);
        if (valueKey != oldKey) {
            string selection = networks.SelectedItem == null ? "" : networks.SelectedItem.ToString();
            networks.Items.Clear(); networks.Items.AddRange(values.ToArray()); networkValues = values.ToArray();
            if (values.Count > 0) networks.SelectedIndex = values.Contains(selection) ? values.IndexOf(selection) : 0;
        }
        string hint = values.Count == 0 ? "未检测到局域网，请先连接 Wi-Fi" : paused ? "接收已暂停" : ready ? "接收端已就绪 · " + service.Port : "输入桥接启动中…";
        statusLabel.Text = "● " + (String.IsNullOrEmpty(error) ? hint : error);
        statusLabel.ForeColor = !String.IsNullOrEmpty(error) || !ready ? Color.SandyBrown : Mint;
        deviceLabel.Text = connected ? "已连接：" + Convert.ToString(state["device"]) : "等待手机连接";
        var keys = ((System.Collections.IEnumerable)state["keys"]).Cast<object>().ToArray(); keysLabel.Text = keys.Length == 0 ? "等待操作" : String.Join("  +  ", keys);
        pause.Text = paused ? "恢复输入" : "暂停输入"; pause.Enabled = reset.Enabled = true;
        power.Text = "停止接收";
        await UpdateQr();
    }
}

internal static class Program {
    [STAThread] private static void Main(string[] args) {
        if (args.Length == 2 && args[0] == "--self-test") {
            Environment.ExitCode = SelfTest(args[1]).GetAwaiter().GetResult();
            return;
        }
        bool first;
        using (var mutex = new Mutex(true, "Local\\OrbitPadReceiverDesktop", out first)) {
            if (!first) { MessageBox.Show("Orbit Pad 接收端已经运行，请检查任务栏或系统托盘。", "Orbit Pad"); return; }
            Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new ReceiverForm());
        }
    }
    private static async Task<int> SelfTest(string destination) {
        var result = new Dictionary<string, object>(); var service = new ReceiverService();
        try {
            await service.Start(); var state = await service.Status();
            result["ready"] = state["ready"]; result["version"] = state["version"]; result["port"] = service.Port;
            if (!Convert.ToBoolean(state["ready"])) throw new Exception("Native keyboard bridge did not become ready");
            await service.Command("pause", new { paused = true });
            if (!Convert.ToBoolean((await service.Status())["paused"])) throw new Exception("Pause failed");
            await service.Command("pause", new { paused = false });
            if (Convert.ToBoolean((await service.Status())["paused"])) throw new Exception("Resume failed");
            await service.Command("reset", new { });
            var urls = ((System.Collections.IEnumerable)state["urls"]).Cast<object>().ToArray();
            if (urls.Length > 0) {
                var png = await service.Qr(new Uri(Convert.ToString(urls[0])).Host);
                using (var stream = new MemoryStream(png)) using (var image = Image.FromStream(stream)) {
                    if (image.Width != 360 || image.Height != 360) throw new Exception("Invalid QR image");
                }
                result["qr"] = "PNG 360x360";
            }
            result["keys"] = state["keys"]; result["ok"] = true;
        } catch (Exception error) { result["ok"] = false; result["error"] = error.ToString(); }
        await service.Stop(); result["stopped"] = !service.Running; service.Dispose();
        File.WriteAllText(destination, new JavaScriptSerializer().Serialize(result), new UTF8Encoding(false));
        return Convert.ToBoolean(result["ok"]) ? 0 : 1;
    }
}
