using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;

// Hardware fixture: routes only to the separately started preview receiver.
internal static class DirectProbe {
    private static int Main(string[] args) {
        var report = new Dictionary<string, object>();
        using (var direct = new DirectConnections(Int32.Parse(args[0]), args[3])) {
            try {
                report["bluetooth"] = direct.StartBluetooth().GetAwaiter().GetResult();
                report["usb"] = direct.ConnectUsb(args[2]).GetAwaiter().GetResult();
                report["ok"] = true;
                File.WriteAllText(args[1], new JavaScriptSerializer().Serialize(report), new UTF8Encoding(false));
                Console.WriteLine("Direct connections ready.");
                DateTime deadline = DateTime.UtcNow.AddMinutes(30);
                while (!File.Exists(args[1] + ".stop") && DateTime.UtcNow < deadline) Thread.Sleep(200);
                return 0;
            } catch (Exception error) {
                report["ok"] = false; report["error"] = error.ToString();
                File.WriteAllText(args[1], new JavaScriptSerializer().Serialize(report), new UTF8Encoding(false));
                Console.Error.WriteLine(error); return 1;
            }
        }
    }
}
