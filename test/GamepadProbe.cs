using System;
using System.Runtime.InteropServices;

// Reads gamepad input; explicit motor arguments exercise XInputSetState on a selected test slot.
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
    [StructLayout(LayoutKind.Sequential)] private struct Vibration { public ushort Large,Small; }
    [DllImport("xinput1_4.dll", EntryPoint="XInputSetState")] private static extern uint SetState(uint index,ref Vibration vibration);
    private static void Main(string[] args) {
        if(args.Length>=3){var motors=new Vibration {Large=UInt16.Parse(args[1]),Small=UInt16.Parse(args[2])};Console.WriteLine(SetState(UInt32.Parse(args[0]),ref motors));if(args.Length>3)System.Threading.Thread.Sleep(Int32.Parse(args[3]));return;}

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
