package pl.shortcuts.app.shizuku;

interface IShellService {
    // Reserved transaction code required by Shizuku for user services.
    void destroy() = 16777114;

    // Runs a command with `sh -c` as the shell user. Returns "<exitCode>\n<output>".
    String exec(String command) = 1;
}
