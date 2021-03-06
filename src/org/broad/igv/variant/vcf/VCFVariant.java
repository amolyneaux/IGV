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

package org.broad.igv.variant.vcf;

import org.apache.log4j.Logger;
import org.broad.igv.variant.Allele;
import org.broad.igv.variant.Genotype;
import org.broad.igv.variant.Variant;
import org.broad.igv.variant.VariantTrack;
import org.broadinstitute.variant.variantcontext.VariantContext;
import org.broadinstitute.variant.variantcontext.VariantContextBuilder;

import java.util.*;

/**
 * @author Jim Robinson, jacob
 * @date Aug 1, 2011
 */
public class VCFVariant implements Variant {

    private static Logger log = Logger.getLogger(Variant.class);

    VariantContext variantContext;
    List<Allele> alternateAlleles;
    private ZygosityCount zygosityCount;

    String chr;
    private double[] alleleFreqs;
    private double methylationRate = Double.NaN;  // <= signals unknown / not applicable
    private double coveredSampleFraction = Double.NaN;

    Map<String, VCFGenotype> genotypeMap;

    private int start = -1;

    public VCFVariant(VariantContext variantContext, String chr) {
        this.variantContext = variantContext;
        this.chr = chr;
        init();
    }

    private void init() {

        // Copy the genotype map.  Calls to variantContext.getGenotype() are expensive
        genotypeMap = new HashMap<String, VCFGenotype>();
        for (String sample : getSampleNames()) {
            org.broadinstitute.variant.variantcontext.Genotype genotype = variantContext.getGenotype(sample);
            VCFGenotype vcfGenotype = genotype == null ? null : new VCFGenotype(genotype);
            genotypeMap.put(sample, vcfGenotype);
        }

        zygosityCount = new ZygosityCount();
        for (String sample : getSampleNames()) {
            Genotype genotype = getGenotype(sample);
            zygosityCount.incrementCount(genotype);
        }

        String afString = null;
        String[] alleleFreqKeys = {"AF", "GMAF"};
        try {
            for (String alleleFreqKey : alleleFreqKeys) {
                afString = variantContext.getAttributeAsString(alleleFreqKey, "-1");
                alleleFreqs = parseAFString(afString);
                if (alleleFreqs[0] >= 0) break;
            }
        } catch (NumberFormatException e) {
            alleleFreqs = new double[]{-1};
            log.error("Error parsing allele frequency: " + afString);
        }

    }

    /**
     * Allele frequency is a comma separated list of doubles
     * We strip away brackets and parentheses
     *
     * @param afString
     * @return
     */
    private double[] parseAFString(String afString) {
        afString = afString.replaceAll("[\\[\\]\\(\\)]", "");
        String[] tokens = afString.split(",");
        double[] result = new double[tokens.length];
        for (int ii = 0; ii < tokens.length; ii++) {
            result[ii] = Double.parseDouble(tokens[ii]);
        }
        return result;
    }

    /**
     * Compute the average methylation rate for those samples with data (i.e. with methylation rate recorded).
     */
    private void computeMethylationRate() {

        double methTotal = 0;
        int samplesWithData = 0;
        final int size = getSampleNames().size();
        if (size > 0) {
            for (String sample : getSampleNames()) {
                Genotype genotype = getGenotype(sample);
                double mr = genotype.getAttributeAsDouble("MR");
                double goodBaseCount = genotype.getAttributeAsDouble("MR");
                if (!Double.isNaN(mr) && !Double.isNaN(goodBaseCount) && goodBaseCount > VariantTrack.METHYLATION_MIN_BASE_COUNT) {
                    methTotal += mr;
                    samplesWithData++;
                }
            }
            methylationRate = samplesWithData == 0 ? 0 : methTotal / samplesWithData;
            coveredSampleFraction = ((double) samplesWithData) / size;
        }
    }


    public String getID() {
        return variantContext.getID();
    }

    public boolean isFiltered() {
        return variantContext.isFiltered();
    }

    public String getAttributeAsString(String key) {
        return variantContext.getAttributeAsString(key, null);
    }

    public String getReference() {
        return variantContext.getReference().toString();
    }

    public List<Allele> getAlternateAlleles() {
        if (alternateAlleles == null) {
            List<org.broadinstitute.variant.variantcontext.Allele> tmp = variantContext.getAlternateAlleles();
            alternateAlleles = new ArrayList<Allele>(tmp.size());
            for (org.broadinstitute.variant.variantcontext.Allele a : tmp) {
                alternateAlleles.add(new VCFAllele(a.getBases()));
            }
        }
        return alternateAlleles;
    }

    public double getPhredScaledQual() {
        return variantContext.getPhredScaledQual();
    }

    public String getType() {
        return variantContext.getType().toString();
    }


    /**
     * Return the allele frequency as annotated with an AF or GMAF attribute.  A value of -1 indicates
     * no annotation (unknown allele frequency).
     */
    public double[] getAlleleFreqs() {
        return alleleFreqs;
    }

    /**
     * Return the allele fraction for this variant.  The allele fraction is similiar to allele frequency, but is based
     * on the samples in this VCF as opposed to an AF or GMAF annotation.
     * <p/>
     * A value of -1 indicates unknown
     */
    public double getAlleleFraction() {

        int total = getHomVarCount() + getHetCount() + getHomRefCount();
        return total == 0 ? -1 : (((double) getHomVarCount() + ((double) getHetCount()) / 2) / total);
    }

    /**
     * Return the methylation rate as annoted with a MR attribute.  A value of -1 indicates
     * no annotation (unknown methylation rate).  This option is only applicable for dna methylation data.
     */
    public double getMethlationRate() {
        if (Double.isNaN(methylationRate)) {
            computeMethylationRate();
        }
        return methylationRate;
    }

    public double getCoveredSampleFraction() {
        if (Double.isNaN(coveredSampleFraction)) {
            computeMethylationRate();
        }
        return coveredSampleFraction;
    }

    public Collection<String> getSampleNames() {
        return variantContext.getSampleNames();
    }

    public Map<String, Object> getAttributes() {
        return variantContext.getAttributes();
    }

    @Override
    public Genotype getGenotype(String sample) {
        return genotypeMap.get(sample);
    }

    public Collection<String> getFilters() {
        return variantContext.getFilters();
    }

    @Override
    public int getHomVarCount() {
        return zygosityCount.getHomVar();
    }

    @Override
    public int getHetCount() {
        return zygosityCount.getHet();
    }

    @Override
    public int getHomRefCount() {
        return zygosityCount.getHomRef();
    }

    @Override
    public int getNoCallCount() {
        return zygosityCount.getNoCall();
    }

    @Override
    public String getChr() {
        return chr;
    }

    @Override
    public int getStart() {
        if(this.start < 0){
            calcStart();
        }
        return this.start;
    }

    @Override
    public int getEnd() {
        return variantContext.getEnd();
    }

    @Override
    public String toString() {
        return String.format("VCFVariant[%s:%d-%d]", getChr(), getStart(), getEnd());
    }

    @Override
    public String getPositionString() {
        if (variantContext.getStart() == variantContext.getEnd()) {
            return String.valueOf(variantContext.getStart());
        } else {
            return String.format("%d-%d", variantContext.getStart(), variantContext.getEnd());
        }

    }

    public String getSource(){
        return variantContext.getSource();
    }

    public VariantContext getVariantContext() {
        return variantContext;
    }

    public static VariantContext getVariantContext(Variant variant) {
        if (variant instanceof VCFVariant) {
            return ((VCFVariant) variant).getVariantContext();
        } else {
            List<org.broadinstitute.variant.variantcontext.Allele> alleleList = new ArrayList<org.broadinstitute.variant.variantcontext.Allele>(variant.getAlternateAlleles().size() + 1);
            alleleList.add(org.broadinstitute.variant.variantcontext.Allele.create(variant.getReference(), true));
            for (Allele all : variant.getAlternateAlleles()) {
                alleleList.add(org.broadinstitute.variant.variantcontext.Allele.create(all.getBases(), false));
            }
            VariantContextBuilder vcb = new VariantContextBuilder(variant.getID(), variant.getChr(), variant.getStart(), variant.getEnd(), alleleList);
            return vcb.make();
        }
    }

    /**
     * @author Jim Robinson
     * @date Aug 1, 2011
     */
    public static class ZygosityCount {
        private int homVar = 0;
        private int het = 0;
        private int homRef = 0;
        private int noCall = 0;

        public void incrementCount(Genotype genotype) {
            if (genotype != null) {
                if (genotype.isHomVar()) {
                    homVar++;
                } else if (genotype.isHet()) {
                    het++;
                } else if (genotype.isHomRef()) {
                    homRef++;
                } else {
                    noCall++;
                }
            }
        }

        public int getHomVar() {
            return homVar;
        }

        public int getHet() {
            return het;
        }

        public int getHomRef() {
            return homRef;
        }

        public int getNoCall() {
            return noCall;
        }

    }

    /**
     * VCFs specify padding bases at the beginning of indels so they can be positioned properly.
     * We display the variant only where it actually differs from the reference. So we find the longest
     * common prefix between the reference and variants
     */
    private void calcStart(){
        int prefixLength = 0;

        if(variantContext.getType() == VariantContext.Type.INDEL || variantContext.getType() == VariantContext.Type.MIXED){
            prefixLength = findCommonPrefixLength();
        }
        this.start = (variantContext.getStart() - 1) + prefixLength;
    }

    /**
     * Find the length of the common prefix between the reference and ALL
     * variant alleles
     * @return
     */
    private int findCommonPrefixLength(){
        String ref = variantContext.getReference().getDisplayString();
        int prefixLength = 0;
        boolean foundmisMatch = false;
        for(int refPos = 0; refPos < ref.length() ; refPos++){
            char refChar = ref.charAt(refPos);
            for(Allele var: getAlternateAlleles()){
                byte[] varBases = var.getBases();
                if(refPos >= varBases.length || varBases[refPos] != refChar){
                    foundmisMatch = true;
                    break;
                }
            }
            if(foundmisMatch){
                break;
            }else{
                prefixLength++;
            }
        }
        return prefixLength;
    }
}
