/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

package org.broad.igv.feature;

import org.apache.log4j.Logger;
import org.broad.igv.Globals;
import org.broad.igv.exceptions.ParserException;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.tribble.GFFCodec;
import org.broad.igv.renderer.GeneTrackRenderer;
import org.broad.igv.renderer.IGVFeatureRenderer;
import org.broad.igv.track.*;
import org.broad.igv.ui.IGV;
import org.broad.igv.util.ParsingUtils;
import org.broad.igv.util.ResourceLocator;
import org.broad.tribble.Feature;
import org.broad.tribble.FeatureCodec;

import java.io.*;
import java.util.*;

/**
 * @deprecated   use org.broad.igv.track.GFFFeatureSource
 * User: jrobinso
 */


public class GFFParser implements FeatureParser {

    static Logger log = Logger.getLogger(GFFParser.class);

    private TrackProperties trackProperties = null;

    public List<org.broad.tribble.Feature> loadFeatures(BufferedReader reader, Genome genome) {
          return loadFeatures(reader, genome, new GFFCodec(genome));
    }

    public List<org.broad.tribble.Feature> loadFeatures(BufferedReader reader, Genome genome, GFFCodec codec) {
        String line = null;
        int lineNumber = 0;
        GFFFeatureSource.GFFCombiner combiner = new GFFFeatureSource.GFFCombiner();
        try {
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.startsWith("#")) {
                    codec.readHeaderLine(line);
                } else {
                    try {
                        Feature f = codec.decode(line);
                        if (f != null) {
                            combiner.addFeature((BasicFeature) f);
                        }
                    } catch (Exception e) {
                        log.error("Error parsing: " + line, e);
                    }
                }
            }


        } catch (IOException ex) {
            log.error("Error reading GFF file", ex);
            if (line != null && lineNumber != 0) {
                throw new ParserException(ex.getMessage(), ex, lineNumber, line);
            } else {
                throw new RuntimeException(ex);
            }
        }

        trackProperties = TrackLoader.getTrackProperties(codec.getHeader());

        //Combine the features
        List<Feature> iFeatures = combiner.combineFeatures();

        if (IGV.hasInstance()) {
            FeatureDB.addFeatures(iFeatures, genome);
        }

        return iFeatures;
    }


    public static Set<String> geneParts = new HashSet();

    static {
        geneParts.add("five_prime_UTR");
        geneParts.add("three_prime_UTR");
        geneParts.add("5'-utr");
        geneParts.add("3'-utr");
        geneParts.add("3'-UTR");
        geneParts.add("5'-UTR");
        geneParts.add("5utr");
        geneParts.add("3utr");
        geneParts.add("CDS");
        geneParts.add("cds");
        geneParts.add("exon");
        geneParts.add("coding_exon");
        geneParts.add("intron");
        geneParts.add("transcript");
        geneParts.add("processed_transcript");
        geneParts.add("mrna");
        geneParts.add("mRNA");

    }

    /**
     * Given a GFF File, creates a new GFF file for each type. Any feature type
     * which is part of a "gene" ( {@link SequenceOntology#geneParts} ) are put in the same file,
     * others are put in different files. So features of type "gene", "exon", and "mrna"
     * would go in gene.gff, but features of type "myFeature" would go in myFeature.gff.
     *
     * @param gffFile
     * @param outputDirectory
     * @throws IOException
     */
    public static void splitFileByType(String gffFile, String outputDirectory) throws IOException {

        BufferedReader br = new BufferedReader(new FileReader(gffFile));
        String nextLine;
        String ext = "." + gffFile.substring(gffFile.length() - 4);

        Map<String, PrintWriter> writers = new HashMap();

        while ((nextLine = br.readLine()) != null) {
            nextLine = nextLine.trim();
            if (!nextLine.startsWith("#")) {
                String[] tokens = Globals.tabPattern.split(nextLine.trim().replaceAll("\"", ""), -1);

                String type = tokens[2];
                if (SequenceOntology.geneParts.contains(type)) {
                    type = "gene";
                }
                if (!writers.containsKey(type)) {
                    writers.put(type,
                            new PrintWriter(new FileWriter(new File(outputDirectory, type + ext))));
                }
            }
        }
        br.close();

        br = new BufferedReader(new FileReader(gffFile));
        PrintWriter currentWriter = null;
        while ((nextLine = br.readLine()) != null) {
            nextLine = nextLine.trim();
            if (nextLine.startsWith("#")) {
                for (PrintWriter pw : writers.values()) {
                    pw.println(nextLine);
                }
            } else {
                String[] tokens = Globals.tabPattern.split(nextLine.trim().replaceAll("\"", ""), -1);
                String type = tokens[2];
                if (SequenceOntology.geneParts.contains(type)) {
                    type = "gene";
                }
                currentWriter = writers.get(type);

                if (currentWriter != null) {
                    currentWriter.println(nextLine);
                } else {
                    System.out.println("No writer for: " + type);
                }
            }

        }

        br.close();
        for (PrintWriter pw : writers.values()) {
            pw.close();
        }
    }

    public TrackProperties getTrackProperties() {
        return trackProperties;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("SpitFilesByType <gffFile> <outputDirectory>");
            return;
        }
        splitFileByType(args[0], args[1]);
    }
}
