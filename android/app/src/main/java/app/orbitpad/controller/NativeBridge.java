package app.orbitpad.controller;

/** Android adapter only. Protocol, session, settings validation and input live in Rust. */
final class NativeBridge {
    static { System.loadLibrary("orbitpad"); }
    static native String call(String command);
    private NativeBridge() {}
}
