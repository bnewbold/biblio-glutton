package com.scienceminer.lookup.command;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.reader.FatcatJsonReader;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import com.scienceminer.lookup.storage.lookup.MetadataLookup;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.XZInputStream;
import java.util.zip.GZIPInputStream;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for loading the fatcat dump in lmdb
 * id -> Json object
 */
public class LoadFatcatCommand extends ConfiguredCommand<LookupConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFatcatCommand.class);

    public static final String fatcat_SOURCE = "fatcat.dump";

    public LoadFatcatCommand() {
        super("fatcat", "Prepare the fatcat database");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument("--input")
                .dest(fatcat_SOURCE)
                .type(String.class)
                .required(true)
                .help("The path to the source file of fatcat dump.");
    }

    @Override
    protected void run(Bootstrap bootstrap, Namespace namespace, LookupConfiguration configuration) throws Exception {

        final MetricRegistry metrics = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();

        reporter.start(15, TimeUnit.SECONDS);

        StorageEnvFactory storageEnvFactory = new StorageEnvFactory(configuration);
        MetadataLookup metadataLookup = new MetadataLookup(storageEnvFactory);
        long start = System.nanoTime();
        final String fatcatFilePath = namespace.get(fatcat_SOURCE);

        LOGGER.info("Preparing the system. Loading data from Fatcat dump from " + fatcatFilePath);

        // fatcat IDs
        InputStream inputStreamFatcat = Files.newInputStream(Paths.get(fatcatFilePath));
        if (fatcatFilePath.endsWith(".xz")) {
            inputStreamFatcat = new XZInputStream(inputStreamFatcat);
        } else if (fatcatFilePath.endsWith(".gz")) {
            inputStreamFatcat = new GZIPInputStream(inputStreamFatcat);
        }
        metadataLookup.loadFromFile(inputStreamFatcat, new FatcatJsonReader(configuration),
                metrics.meter("fatcatLookup"));
        LOGGER.info("Fatcat lookup loaded " + metadataLookup.getSize() + " records. ");

        LOGGER.info("Finished in " +
                TimeUnit.SECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " s");
    }
}
