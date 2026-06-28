/**
 * Signals a user-facing error. The message is printed (without a stack trace)
 * and the program exits with status 1.
 */
class CliError extends RuntimeException {
    CliError(String message) {
        super(message);
    }
}
