//! Line-based harness for exercising the same engine the APK loads through JNI.
use std::io::{self, BufRead, Write};
fn main() {
    for line in io::stdin().lock().lines() {
        let Ok(line) = line else {
            break;
        };
        println!("{}", orbitpad::dispatch(&line));
        io::stdout().flush().unwrap();
    }
}
