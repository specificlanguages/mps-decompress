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
	var showHelp, showVersion bool

	flags := flag.NewFlagSet("mps-decompress", flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	flags.BoolVar(&showHelp, "h", false, "print help and exit")
	flags.BoolVar(&showHelp, "help", false, "print help and exit")
	flags.BoolVar(&showVersion, "version", false, "print version and exit")
	flags.BoolVar(&showVersion, "v", false, "print version and exit")

	if err := flags.Parse(os.Args[1:]); err != nil {
		fmt.Fprintf(os.Stderr, "%v\n", err)
		os.Exit(2)
	}

	if showHelp {
		printUsage(os.Stdout)
		return
	}

	if showVersion {
		fmt.Printf("mps-decompress %s\n", version)
		return
	}

	if flags.NArg() > 1 {
		fmt.Fprintln(os.Stderr, "expected zero or one input path")
		os.Exit(2)
	}

	var (
		input io.Reader = os.Stdin
		file  *os.File
	)

	if flags.NArg() == 1 {
		path := flags.Arg(0)
		var err error
		file, err = os.Open(path)
		if err != nil {
			fmt.Fprintf(os.Stderr, "read %s: %v\n", path, err)
			os.Exit(1)
		}
		defer file.Close()
		input = file
	}

	if err := decompress.Transform(input, os.Stdout); err != nil {
		fmt.Fprintf(os.Stderr, "%v\n", err)
		os.Exit(1)
	}
}

func printUsage(w io.Writer) {
	fmt.Fprintln(w, "Usage: mps-decompress [--version] [input.mps]")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Reads from stdin when input.mps is omitted. Writes transformed XML to stdout.")
}
