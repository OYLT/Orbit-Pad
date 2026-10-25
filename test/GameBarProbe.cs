using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Windows.Forms;

// Capture and suppress only the injected Game Bar chord during this test.
internal static class GameBarProbe {
    private delegate IntPtr Hook(int code, IntPtr message, IntPtr data);
    private static readonly Hook callback = Capture;
    private static readonly List<string> events = new List<string>();
    private static IntPtr handle;
    [StructLayout(LayoutKind.Sequential)] private struct Keyboard { public uint Key, Scan, Flags, Time; public UIntPtr Extra; }
    [DllImport("user32.dll", SetLastError=true)] private static extern IntPtr SetWindowsHookEx(int id, Hook hook, IntPtr module, uint thread);
    [DllImport("user32.dll")] private static extern bool UnhookWindowsHookEx(IntPtr hook);
    [DllImport("user32.dll")] private static extern IntPtr CallNextHookEx(IntPtr hook, int code, IntPtr message, IntPtr data);
    [DllImport("kernel32.dll")] private static extern IntPtr GetModuleHandle(string module);
    private static IntPtr Capture(int code, IntPtr message, IntPtr data) {
        if (code >= 0) {
            Keyboard key=(Keyboard)Marshal.PtrToStructure(data,typeof(Keyboard));
            if ((key.Flags & 0x10)!=0 && (key.Key==0x5B || key.Key==0x47)) {
                events.Add("\""+key.Key+":"+((key.Flags & 0x80)!=0 ? "up" : "down")+"\"");
                return (IntPtr)1;
            }
        }
        return CallNextHookEx(handle,code,message,data);
    }
    [STAThread] private static int Main() {
        handle=SetWindowsHookEx(13,callback,GetModuleHandle(null),0);
        if(handle==IntPtr.Zero)return 2;
        try {
            using(var timer=new Timer { Interval=4500 }) {
                timer.Tick+=(sender,args)=>Application.ExitThread();timer.Start();
                Console.WriteLine("READY");Application.Run();
            }
            Console.WriteLine("["+String.Join(",",events.ToArray())+"]");return 0;
        } finally {UnhookWindowsHookEx(handle);}
    }
}
