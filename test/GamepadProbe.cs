using System;
using System.Runtime.InteropServices;

// Reads the standard Windows API only. It never injects input into any device.
internal static class GamepadProbe {
    [StructLayout(LayoutKind.Sequential)]
    private struct State {
        public uint Packet;
        public ushort Buttons;
        public byte LeftTrigger, RightTrigger;
        public short LeftX, LeftY, RightX, RightY;
    }
    [DllImport("xinput1_4.dll", EntryPoint = "XInputGetState")]
    private static extern uint GetState(uint index, out State state);
    private static void Main() {
        Console.Write("[");
        for (uint i = 0; i < 4; i++) {
            State s;
            uint error = GetState(i, out s);
            if (i > 0) Console.Write(",");
            if (error != 0) Console.Write("null");
            else Console.Write("{\"buttons\":" + s.Buttons + ",\"leftTrigger\":" + s.LeftTrigger + ",\"rightTrigger\":" + s.RightTrigger
                + ",\"leftX\":" + s.LeftX + ",\"leftY\":" + s.LeftY + ",\"rightX\":" + s.RightX + ",\"rightY\":" + s.RightY + "}");
        }
        Console.WriteLine("]");
    }
}
