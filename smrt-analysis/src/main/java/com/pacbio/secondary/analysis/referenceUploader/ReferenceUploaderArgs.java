/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.lang.StringUtils;

import com.pacbio.secondary.common.core.AnalysisContext;
import com.pacbio.secondary.common.model.ReferenceEntry;

/**
 * Singleton which validates and holds parameters passed into ReferenceUploader
 * on the cmd line.
 * 
 * @author jmiller
 */
public class ReferenceUploaderArgs {

	private static ReferenceUploaderArgs instance;

	// Do not access directly, use getter
	private static Options opts;

	private static CommandLine line;

	private Action action;

	private String cmdLine;

	/**
	 * Do not allow external creation
	 */
	private ReferenceUploaderArgs() {
	}

	/**
	 * Special group of mutually exclusive cmd options. One and ONLY one cmd
	 * must be specified when ReferenceUploader is invoked.
	 */
	public static enum Action {
		Create("c", "create",
				"Create a reference. Requires: -n -f(Multi). Optional: -p"), Delete(
				"d", "delete", "delete a reference"), Hide("h", "hide",
				"Inactivate a reference. Requires --id"), Activate("a",
				"activate", "Activate a reference. Requires --id"), Reindex(
				"i", "reindex", "Regenerate *.index files. Requires --id"), Refresh(
				"r",
				"refresh",
				"Discover references added to the repository directory, add entries to index.xml"), Upgrade(
				"g",
				"upgrade",
				"Upgrade the reference repository between SMRTAnalysisReleases. Optional: --argbag"), ;

		private String optShort;
		private String optLong;
		private String desc;

		Action(String optShort, String optLong, String desc) {
			this.optShort = optShort;
			this.optLong = optLong;
			this.desc = desc;
		}
	}

	/**
	 * Create a {@link ReferenceUploaderArgs} singleton. This is the only place
	 * is should be created.
	 * 
	 * @param args
	 * @throws ParseException
	 */
	public static void CreateInstance(String[] args) throws ParseException {

		instance = new ReferenceUploaderArgs();

		instance.setCmdLineArgs(args);

		// ---- Super set of all options defined here ----//
		Options opts = getOpts();

		CommandLineParser parser = new PosixParser();
		line = parser.parse(opts, args);

		// check for help first
		if (line.hasOption("help")) {
			getUsage();
			return;
		}

		if (line.hasOption('c')) {
			instance.setAction(Action.Create);
		}
		if (line.hasOption('h')) {
			instance.setAction(Action.Hide);
		}
		if (line.hasOption('d')) {
			instance.setAction(Action.Delete);
		}
		if (line.hasOption('a')) {
			instance.setAction(Action.Activate);
		}
		if (line.hasOption('i')) {
			instance.setAction(Action.Reindex);
		}
		if (line.hasOption('r')) {
			instance.setAction(Action.Refresh);
		}
		if (line.hasOption('g')) {
			instance.setAction(Action.Upgrade);
		}

		validateCmdSpecificOptions();
		validateFormat();
	}

	public boolean showHelp() {
		return line.hasOption("help");
	}

	private static void validateFormat() throws ParseException {
		if (instance.getUri() != null) {
			try {
				new URL(instance.getUri());
			} catch (MalformedURLException e) {
				throw new ParseException("Invalid uri: " + e.getMessage());
			}
		}

	}

	private static void validateCmdSpecificOptions() throws ParseException {
		if (instance().getAction() == null)
			throw new ParseException("No cmd specified: " + getCmdString());

		if (instance().getAction() == Action.Create) {
			if (!line.hasOption('n') || !line.hasOption('f')) {
				throw new ParseException("-c requires -n, -f(Multi)");
			}
		}
		if (instance().getAction() == Action.Hide) {
			if (!line.hasOption("id")) {
				throw new ParseException("-h requires --id");
			}
		}
		if (instance().getAction() == Action.Activate) {
			if (!line.hasOption("id")) {
				throw new ParseException("-a requires --id");
			}
		}
		if (instance().getAction() == Action.Delete) {
			if (!line.hasOption("id")) {
				throw new ParseException("-d requires --id");
			}
		}
		if (instance().getAction() == Action.Reindex) {
			if (!line.hasOption("id")) {
				throw new ParseException("-i requires --id");
			}
		}
		if (instance().getAction() == Action.Upgrade) {
			if (!line.hasOption("id") || !line.hasOption("name")) {
				throw new ParseException("-g requires --id, --name");
			}
		}
	}

	/**
	 * Helpful string reminding user to supply an action
	 * 
	 * @return
	 */
	private static String getCmdString() {
		StringBuffer sb = new StringBuffer();
		for (Action a : Action.values()) {
			sb.append("--").append(a.optLong).append(" ");
		}
		return sb.toString();
	}

	/**
	 * Get statically created options.
	 * 
	 * @return
	 */
	private static Options getOpts() {
		if (opts != null)
			return opts;

		opts = new Options();

		// cmd enums first
		opts.addOption(buildOption(Action.Create.optShort,
				Action.Create.optLong, false, Action.Create.desc));
		opts.addOption(buildOption(Action.Delete.optShort,
				Action.Delete.optLong, false, Action.Delete.desc));
		opts.addOption(buildOption(Action.Hide.optShort, Action.Hide.optLong,
				false, Action.Hide.desc));
		opts.addOption(buildOption(Action.Activate.optShort,
				Action.Activate.optLong, false, Action.Activate.desc));
		opts.addOption(buildOption(Action.Refresh.optShort,
				Action.Refresh.optLong, false, Action.Refresh.desc));
		opts.addOption(buildOption(Action.Reindex.optShort,
				Action.Reindex.optLong, false, Action.Reindex.desc));
		opts.addOption(buildOption(Action.Upgrade.optShort,
				Action.Upgrade.optLong, false, Action.Upgrade.desc));

		// all others
		opts.addOption(buildOption("n", "name", true,
				"The name of the reference"));
		opts.addOption(buildOption(
				"f",
				"fastaFile",
				true,
				"Full path to a fasta input file. You may repeat this option, for > 1 fasta file."));
		opts.addOption(buildOption(
				"p",
				"refRepos",
				true,
				"Reference repository location. "
						+ "The cmd line option takes precedence. Else, defaults to what can be discovered from "
						+ "SEYMOUR_HOME/etc/config.xml. Failing that, assumes /opt/smrtanalysis/common/references."));
		opts.addOption(buildOption(
				"s",
				"saw",
				true,
				"SAWriter command. Supply this option if you want to run the Suffix Array writer. "
						+ "If sawriter is in the path, the path does not need to be qualified. All options except input/output "
						+ "files should be supplied, as in:  "
						+ "sawriter -blt 8 -welter"));

//		opts.addOption(buildOption(
//				"k",
//				"gatkDict",
//				true,
//				"GATK sequence dictionary command. If createSequenceDictionary is in the path, "
//						+ "the path does not need to be qualified. "
//						+ "All options except input/output files should be supplied."));

		opts.addOption(buildOption(
				"x",
				"samIdx",
				true,
				"samtools index. If samtools is in the path, "
						+ "the path does not need to be qualified. "
						+ "All options except input/output files should be supplied, as in: 'samtools faidx'."));

		opts.addOption(buildOption("t", "control", false,
				"Flag to create a control reference."));

		opts.addOption(buildOption("l", "lineLength", true,
				"The fasta sequence line length. Default == 60 chars."));

		opts.addOption(buildOption("o", "organism", true, "Organism name"));

		opts.addOption(buildOption("u", "user", true, "User"));

		opts.addOption(buildOption("e", "desc", true, "Description"));

		opts.addOption(buildOption(null, "id", true, "Reference id"));

		opts.addOption(buildOption(null, "help", false, "Show usage"));

		opts.addOption(buildOption(null, "verbose", false,
				"Turn on verbose logging. Also prints log messages to the console."));

		opts.addOption(buildOption(null, "uri", true,
				"Uri to which status messages can be posted."));

		opts.addOption(buildOption(null, "progress", true,
				"Integer: minutes. How often to update progress. Defaults to 1 minute."));

		opts.addOption(buildOption(
				null,
				"argbag",
				true,
				"For use with the upgrade command. Since this is a generic command that performs different operations "
						+ "between releases of the SMRTAnalysis system, the required inputs may vary. This is a key-value list."));

		opts.addOption(buildOption(
				null,
				"skipIndexUpdate",
				false,
				"Flag used in conjuction with the create flag. Create the reference without updating index xml."));
		
		opts.addOption(buildOption(
				null,
				"jobId",
				true,
				"Pass a job identifier (jobId) into the index.xml entry for this reference. Used in conjunction with -c."));
		
		opts.addOption(buildOption(
				null,
				"ploidy",
				true,
				"The ploidy of the organism, e.g. 'haploid' or 'diploid'"));
		
		opts.addOption(buildOption(
				null,
				"gmapdb",
				true,
				"Build the gmap db. If the exe is in the path, "
						+ "it does not need to be qualified. "
						+ "All options except input/output files should be supplied, as in: 'gmap_build --db=gmap_db'."));

		return opts;
	}

	/**
	 * Using the OptionBuilder allows us to create options that have no short
	 * name. However, a long name and description are required,
	 * 
	 * @param shortName
	 *            - can be null
	 * @param longName
	 * @param hasArg
	 * @param desc
	 * @return Option
	 */
	@SuppressWarnings("static-access")
	private static Option buildOption(String shortName, String longName,
			boolean hasArg, String desc) {
		OptionBuilder ob = OptionBuilder.hasArg(hasArg).withLongOpt(longName)
				.withDescription(desc);
		if (shortName != null)
			return ob.create(shortName);
		return ob.create();
	}

	private void setAction(Action action) throws ParseException {
		if (this.action != null) {
			throw new ParseException("You cannot supply " + this.action
					+ " and " + action + " at the same time.");
		}
		this.action = action;
	}

	/**
	 * Get the singleton action
	 * 
	 * @return
	 */
	public Action getAction() {
		return this.action;
	}

	/**
	 * @return {@link ReferenceUploaderArgs} The singleton container of options
	 */
	public static ReferenceUploaderArgs instance() {
		return instance;
	}

	/**
	 * Print cmd line usage to the console.
	 */
	public static void getUsage() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(ReferenceUploader.class.getName(), getOpts());
	}

	/**
	 * @return Array of fastaFiles passed in
	 */
	public String[] getFastaFiles() {
		return line.getOptionValues('f');
	}

	/**
	 * If a repository location was specified on the cmd line, return that.
	 * Else, return what {@link AnalysisContext#getReferencesRoot()} returns.
	 * 
	 * @return
	 */
	public String getRefRepos() {
		String r = line.getOptionValue('p');
		if (StringUtils.isNotBlank(r))
			return r;
		return AnalysisContext.getInstance().getReferencesRoot()
				.getAbsolutePath();
	}

	/**
	 * @return String name of the reference
	 */
	public String getName() {
		return line.getOptionValue('n');
	}

	/**
	 * The sawriter command, including all options except input/output files.
	 * For example: sawriter -blt 8 -welter
	 * 
	 * @return
	 */
	public String getSawCommand() {
		return line.getOptionValue('s');
	}

	/**
	 * Get the type of reference being created. This is controlled by the
	 * "--control" option. If nothing is specified, defaults to
	 * {@link ReferenceEntry.Type#Sample}
	 * 
	 * @return
	 */
	public ReferenceEntry.Type getRefType() {
		if (line.hasOption('t'))
			return ReferenceEntry.Type.Control;
		return ReferenceEntry.Type.Sample;
	}

	/**
	 * Organism name
	 * 
	 * @return
	 */
	public String getOrganism() {
		return line.getOptionValue('o');
	}

	/**
	 * User-supplied reference id
	 * 
	 * @return
	 */
	public String getRefId() {
		return line.getOptionValue("id");
	}

	/**
	 * Cmd line that the user typed in
	 * 
	 * @return
	 */
	public String getCmdLine() {
		return this.cmdLine;
	}

	/**
	 * For logging purposes, store the cmd line
	 * 
	 * @param args
	 */
	private void setCmdLineArgs(String[] args) {
		StringBuffer sb = new StringBuffer();
		for (String s : args)
			sb.append(s).append(" ");
		this.cmdLine = sb.toString();

	}

	/**
	 * User
	 * 
	 * @return
	 */
	public String getUser() {
		return line.getOptionValue('u');
	}

	/**
	 * Ref desc
	 * 
	 * @return
	 */
	public String getDesc() {
		String d = line.getOptionValue('e');
		if (d == null)
			d = "";
		return d;
	}

	/**
	 * Flag that indicates whether to log fine-grained messages
	 * 
	 * @return
	 */
	public boolean verbose() {
		return line.hasOption("verbose");
	}

	/**
	 * Get the uri to post notifications to
	 * 
	 * @return
	 */
	public String getUri() {
		return line.getOptionValue("uri");
	}

	/**
	 * How often, in minutes, to update progress. Defaults to 1.
	 * 
	 * @return progressFrequency
	 */
	public int getProgressFrequency() {
		try {
			return Integer.parseInt(line.getOptionValue("progress"));
		} catch (Exception e) {
			return 1;
		}
	}

	/**
	 * Get the argbag (kv list) that the Upgrade command needs.
	 * 
	 * @return argbag
	 */
	public String getUpgradeArgbag() {
		return line.getOptionValue("argbag");
	}

	public boolean getSkipUpdateIndexXml() {
		return line.hasOption("skipIndexUpdate");
	}

	/**
	 * Get the fasta sequence line length. Default is 60 chars.
	 * 
	 * @return
	 */
	public int getLineLength() {
		try {
			return Integer.parseInt(line.getOptionValue("l"));
		} catch (Exception e) {
			return Constants.FASTA_LINE_LENGTH;
		}
	}

//	public String getGatkDictCommand() {
//		return line.getOptionValue('k');
//	}

	public String getSamIdxCommand() {
		return line.getOptionValue("x");
	}
	
	public String getGmapDbCommand() {
		return line.getOptionValue("gmapdb");
	}

	public String getJobId() {
		return line.getOptionValue("jobId");
	}

	public String getPloidy() {
		return line.getOptionValue("ploidy");
	}

	

}
