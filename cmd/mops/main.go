package main

import (
	"flag"
	"fmt"
	"io"
	"os"

	"mps-decompress/internal/decompress"
)

const version = "0.1.0"

func main() {
	os.Exit(run(os.Args[1:], os.Stdin, os.Stdout, os.Stderr))
}

func run(args []string, stdin io.Reader, stdout, stderr io.Writer) int {
	var showHelp, showVersion bool

	flags := flag.NewFlagSet("mops", flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	flags.BoolVar(&showHelp, "h", false, "print help and exit")
	flags.BoolVar(&showHelp, "help", false, "print help and exit")
	flags.BoolVar(&showVersion, "version", false, "print version and exit")
	flags.BoolVar(&showVersion, "v", false, "print version and exit")

	if err := flags.Parse(args); err != nil {
		fmt.Fprintln(stderr, err)
		return 2
	}

	if showHelp {
		printUsage(stdout)
		return 0
	}

	if showVersion {
		fmt.Fprintf(stdout, "mops %s\n", version)
		return 0
	}

	if flags.NArg() == 0 {
		fmt.Fprintln(stderr, "expected command")
		printUsage(stderr)
		return 2
	}

	command := flags.Arg(0)
	commandArgs := flags.Args()[1:]

	switch command {
	case "decompress":
		return runDecompress(commandArgs, stdin, stdout, stderr)
	default:
		fmt.Fprintf(stderr, "unknown command: %s\n", command)
		printUsage(stderr)
		return 2
	}
}

func runDecompress(args []string, stdin io.Reader, stdout, stderr io.Writer) int {
	var showHelp bool

	flags := flag.NewFlagSet("mops decompress", flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	flags.BoolVar(&showHelp, "h", false, "print help and exit")
	flags.BoolVar(&showHelp, "help", false, "print help and exit")

	if err := flags.Parse(args); err != nil {
		fmt.Fprintln(stderr, err)
		return 2
	}

	if showHelp {
		printDecompressUsage(stdout)
		return 0
	}

	if flags.NArg() > 1 {
		fmt.Fprintln(stderr, "expected zero or one input path")
		return 2
	}

	var (
		input io.Reader = stdin
		file  *os.File
	)

	if flags.NArg() == 1 {
		path := flags.Arg(0)
		var err error
		file, err = os.Open(path)
		if err != nil {
			fmt.Fprintf(stderr, "read %s: %v\n", path, err)
			return 1
		}
		defer file.Close()
		input = file
	}

	if err := decompress.Transform(input, stdout); err != nil {
		fmt.Fprintln(stderr, err)
		return 1
	}

	return 0
}

func printUsage(w io.Writer) {
	fmt.Fprintln(w, "Usage: mops [--version] <command> [args]")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Commands:")
	fmt.Fprintln(w, "  decompress   Expand compressed MPS model XML for inspection")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Run \"mops <command> --help\" for command-specific help.")
}

func printDecompressUsage(w io.Writer) {
	fmt.Fprintln(w, "Usage: mops decompress [input.mps]")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Reads from stdin when input.mps is omitted. Writes transformed XML to stdout.")
}
