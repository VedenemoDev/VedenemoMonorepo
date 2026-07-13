package org.vedenemo.cli;

/**
 * Minimal CLI entry point.
 */
public final class VedenemoCli {

    private VedenemoCli() {
    }

    public static void main(String[] args) {
        CliConfig config = CliConfig.fromEnvironment(System.getenv());
        int exitCode = new VedenemoCliApp(
                new HttpSessionClient(config.apiBaseUrl()),
                new HttpModelClient(config.apiBaseUrl()),
                new HttpCommandClient(config.apiBaseUrl()),
                System.in,
                System.out,
                true
        ).run();
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
