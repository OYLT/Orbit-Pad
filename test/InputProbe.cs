using System;
using System.Drawing;
using System.IO;
using System.Text;
using System.Windows.Forms;

// Receives keys only while this test window is focused; no global keyboard hook.
internal static class InputProbe {
    [STAThread] private static void Main() {
        Application.EnableVisualStyles();
        var form = new Form { Text = "Orbit Pad Input Test", Width = 720, Height = 340,
            BackColor = Color.FromArgb(16,20,19), ForeColor = Color.FromArgb(185,245,128),
            StartPosition = FormStartPosition.CenterScreen, KeyPreview = true };
        var label = new Label { Text = "Waiting for the phone controller...", Dock = DockStyle.Fill,
            TextAlign = ContentAlignment.MiddleCenter, Font = new Font("Segoe UI", 20) };
        form.Controls.Add(label);
        string log = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "input-probe.jsonl");
        Action<string, string> record = (kind, key) => File.AppendAllText(log,
            "{\"kind\":\"" + kind + "\",\"key\":\"" + key + "\",\"time\":\"" + DateTime.UtcNow.ToString("o") + "\"}\n", new UTF8Encoding(false));
        form.KeyDown += (sender, args) => { label.Text = args.KeyCode + " pressed"; record("down", args.KeyCode.ToString()); args.SuppressKeyPress = true; };
        form.KeyUp += (sender, args) => { label.Text = args.KeyCode + " released"; record("up", args.KeyCode.ToString()); };
        form.Activated += (sender, args) => record("focus", "");
        form.Deactivate += (sender, args) => record("blur", "");
        var timeout = new Timer { Interval = 90000 }; timeout.Tick += (sender, args) => form.Close(); timeout.Start();
        Application.Run(form);
    }
}
