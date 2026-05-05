package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"strconv"

	"mops/internal/expand"
	"mops/internal/generateids"
	"mops/internal/listmodels"
	"mops/internal/validate"
	"mops/internal/xmlschema"
)

const version = "0.2.0"
const maxGenerateIDCount = 1_000_000

func main() {
	os.Exit(run(os.Args[1:], os.Stdin, os.Stdout, os.Stderr))
}

func run(args []string, stdin io.Reader, stdout, stderr io.Writer) int {
	_ = xmlschema.LinkedVersion()

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
	case "expand":
		return runExpand(commandArgs, stdin, stdout, stderr)
	case "list-models":
		return runListModels(commandArgs, stdout, stderr)
	case "generate-ids":
		return runGenerateIDs(commandArgs, stdout, stderr)
	case "validate":
		return runValidate(commandArgs, stdout, stderr)
	default:
		fmt.Fprintf(stderr, "unknown command: %s\n", command)
		printUsage(stderr)
		return 2
	}
}

func runExpand(args []string, stdin io.Reader, stdout, stderr io.Writer) int {
	var showHelp bool

	flags := flag.NewFlagSet("mops expand", flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	flags.BoolVar(&showHelp, "h", false, "print help and exit")
	flags.BoolVar(&showHelp, "help", false, "print help and exit")

	if err := flags.Parse(args); err != nil {
		fmt.Fprintln(stderr, err)
		return 2
	}

	if showHelp {
		printExpandUsage(stdout)
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

	if err := expand.Transform(input, stdout); err != nil {
		fmt.Fprintln(stderr, err)
		return 1
	}

	return 0
}

func runGenerateIDs(args []string, stdout, stderr io.Writer) int {
	var showHelp, long bool

	flags := flag.NewFlagSet("mops generate-ids", flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	flags.BoolVar(&showHelp, "h", false, "print help and exit")
	flags.BoolVar(&showHelp, "help", false, "print help and exit")
	flags.BoolVar(&long, "long", false, "print decimal IDs instead of Java-friendly base64 IDs")

	if err := flags.Parse(args); err != nil {
		fmt.Fprintln(stderr, err)
		return 2
	}

	if showHelp {
		printGenerateIDsUsage(stdout)
		return 0
	}

	if flags.NArg() != 2 {
		fmt.Fprintln(stderr, "expected model path and count")
		return 2
	}

	count, err := strconv.Atoi(flags.Arg(1))
	if err != nil || count < 0 {
		fmt.Fprintln(stderr, "count must be a non-negative integer")
		return 2
	}
	if count > maxGenerateIDCount {
		fmt.Fprintf(stderr, "count must be at most %d\n", maxGenerateIDCount)
		return 2
	}

	ids, err := generateids.Generate(flags.Arg(0), count, nil)
	if err != nil {
		fmt.Fprintln(stderr, err)
		return 1
	}

	if long {
		for _, id := range ids {
			fmt.Fprintln(stdout, id)
		}
		return 0
	}

	for _, id := range generateids.Encoded(ids) {
		fmt.Fprintln(stdout, id)
	}
	return 0
}

func runListModels(args []string, stdout, stderr io.Writer) int {
	var showHelp bool

	flags := flag.NewFlagSet("mops list-models", flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	flags.BoolVar(&showHelp, "h", false, "print help and exit")
	flags.BoolVar(&showHelp, "help", false, "print help and exit")

	if err := flags.Parse(args); err != nil {
		fmt.Fprintln(stderr, err)
		return 2
	}

	if showHelp {
		printListModelsUsage(stdout)
		return 0
	}

	if flags.NArg() > 1 {
		fmt.Fprintln(stderr, "expected zero or one root path")
		return 2
	}

	root := "."
	if flags.NArg() == 1 {
		root = flags.Arg(0)
	}

	locations, err := listmodels.Find(root)
	if err != nil {
		fmt.Fprintln(stderr, err)
		return 1
	}

	encoder := json.NewEncoder(stdout)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(jsonLocations(locations)); err != nil {
		fmt.Fprintln(stderr, err)
		return 1
	}

	return 0
}

func runValidate(args []string, stdout, stderr io.Writer) int {
	var showHelp, jsonOutput bool

	flags := flag.NewFlagSet("mops validate", flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	flags.BoolVar(&showHelp, "h", false, "print help and exit")
	flags.BoolVar(&showHelp, "help", false, "print help and exit")
	flags.BoolVar(&jsonOutput, "json", false, "print validation report as JSON")

	if err := flags.Parse(args); err != nil {
		fmt.Fprintln(stderr, err)
		return 2
	}

	if showHelp {
		printValidateUsage(stdout)
		return 0
	}

	if flags.NArg() == 0 {
		fmt.Fprintln(stderr, "expected at least one validation target")
		return 2
	}

	report := validate.Run(flags.Args())
	if jsonOutput {
		encoder := json.NewEncoder(stdout)
		encoder.SetIndent("", "  ")
		if err := validate.WriteJSON(report, encoder); err != nil {
			fmt.Fprintln(stderr, err)
			return 1
		}
		if report.HasErrors() {
			return 1
		}
		return 0
	}

	for _, target := range report.Targets {
		if len(target.Findings) == 0 {
			fmt.Fprintf(stdout, "OK %s\n", target.Target)
			continue
		}
		for _, finding := range target.Findings {
			location := target.Target
			if finding.File != "" {
				location = finding.File
			}
			fmt.Fprintf(stdout, "%s %s: %s\n", finding.Severity, location, finding.Message)
		}
	}

	if report.HasErrors() {
		return 1
	}
	return 0
}

func jsonLocations(locations listmodels.LocationMap) map[string]any {
	result := make(map[string]any, len(locations))
	for ref, paths := range locations {
		if len(paths) == 1 {
			result[ref] = paths[0]
			continue
		}
		result[ref] = paths
	}
	return result
}

func printUsage(w io.Writer) {
	fmt.Fprintln(w, "Usage: mops [--version] <command> [args]")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Commands:")
	fmt.Fprintln(w, "  expand        Expand compressed MPS model XML for inspection")
	fmt.Fprintln(w, "  generate-ids  Generate unused regular node IDs for an MPS model")
	fmt.Fprintln(w, "  list-models   List MPS model IDs and locations as JSON")
	fmt.Fprintln(w, "  validate      Validate MPS model XML persistence")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Run \"mops <command> --help\" for command-specific help.")
}

func printExpandUsage(w io.Writer) {
	fmt.Fprintln(w, "Usage: mops expand [input.mps]")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Expand short indices in MPS model files (.mps, .mpsr, .model files) to full names or identifiers for inspection")
	fmt.Fprintln(w, "Reads from stdin when input.mps is omitted. Writes expanded XML to stdout.")
}

func printGenerateIDsUsage(w io.Writer) {
	fmt.Fprintln(w, "Usage: mops generate-ids [--long] <model.mps|model-folder> <count>")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Scans node@id values in a standalone .mps file or direct .mpsr files in a file-per-root model folder.")
	fmt.Fprintln(w, "Prints one generated regular node ID per line. Defaults to Java-friendly base64; --long prints decimal IDs.")
}

func printListModelsUsage(w io.Writer) {
	fmt.Fprintln(w, "Usage: mops list-models [root]")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Scans root, or the current directory when omitted, for .mps files and file-per-root .model metadata files.")
	fmt.Fprintln(w, "Prints a JSON object mapping model IDs to absolute paths. Duplicate model locations are arrays.")
}

func printValidateUsage(w io.Writer) {
	fmt.Fprintln(w, "Usage: mops validate [--json] <model.mps|model-folder|root.mpsr> [other-targets...]")
	fmt.Fprintln(w)
	fmt.Fprintln(w, "Validates explicit MPS model XML targets.")
}
