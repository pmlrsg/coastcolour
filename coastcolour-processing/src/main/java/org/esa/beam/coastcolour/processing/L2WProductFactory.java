package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.util.ProductUtils;

import java.awt.Color;

/**
 * @author Marco Peters
 * @since 1.4
 */
abstract class L2WProductFactory {

    static final String A_TOTAL_443_SOURCE_NAME = "a_total_443";
    static final String A_YS_443_SOURCE_NAME = "a_ys_443";
    static final String A_PIG_443_SOURCE_NAME = "a_pig_443";
    static final String BB_SPM_443_SOURCE_NAME = "bb_spm_443";
    static final String A_POC_443_SOURCE_NAME = "a_poc_443";
    static final String TSM_SOURCE_NAME = "tsm";
    static final String CHL_CONC_SOURCE_NAME = "chl_conc";
    static final String ALTITUDE_SOURCE_NAME = "altitude";


    static final String IOP_PREFIX_TARGET_BAND_NAME = "iop_";
    public static final String IOP_A_TOTAL_443_NAME = IOP_PREFIX_TARGET_BAND_NAME + A_TOTAL_443_SOURCE_NAME;
    public static final String IOP_A_YS_443_NAME = IOP_PREFIX_TARGET_BAND_NAME + A_YS_443_SOURCE_NAME;
    public static final String IOP_A_PIG_443_NAME = IOP_PREFIX_TARGET_BAND_NAME + A_PIG_443_SOURCE_NAME;
    public static final String IOP_BB_SPM_443_NAME = IOP_PREFIX_TARGET_BAND_NAME + BB_SPM_443_SOURCE_NAME;
    public static final String IOP_A_POC_443_NAME = IOP_PREFIX_TARGET_BAND_NAME + A_POC_443_SOURCE_NAME;
    public static final String CONC_TSM_NAME = "conc_tsm";
    public static final String CONC_CHL_NAME = "conc_chl";

    static final String L2W_FLAGS_NAME = "l2w_flags";
    static final String EXP_FLH_681_NAME = "exp_FLH_681";
    static final String EXP_FLH_681_NORM_NAME = "exp_FLH_681_norm";
    static final String EXP_FLH_681_ALT_NAME = "exp_FLH_681_alt";
    static final String EXP_FLH_NORM_OLD_681_NAME = "exp_FLH_norm_old_681";
    static final String EXP_FLH_ALT_OLD_681_NAME = "exp_FLH_alt_old_681";
    static final String K_MIN_NAME = "K_min";
    static final int[] KD_LAMBDAS = new int[]{412, 443, 490, 510, 560, 620, 664, 680};
    static final String Z90_MAX_NAME = "Z90_max";

    static final String L2W_VALID_EXPRESSION = "!l2w_flags.INVALID";

    protected static final String[] IOP_SOURCE_BAND_NAMES = new String[]{
            A_TOTAL_443_SOURCE_NAME, A_YS_443_SOURCE_NAME,
            A_PIG_443_SOURCE_NAME, BB_SPM_443_SOURCE_NAME
    };
    protected static final String IOP_QUALITY_BAND_NAME = "iop_quality";
    protected static final String IOP_QUALITY_DESCRIPTION = "Quality indicator for IOPs";

    private static final String WLR_OOR_DESCRIPTION = "Water leaving reflectance out of training range";
    private static final String CONC_OOR_DESCRIPTION = "Water constituents out of training range";
    private static final String OOTR_DESCRIPTION = "Spectrum out of training range (chiSquare threshold)";
    private static final String WHITE_CAPS_DESCRIPTION = "Risk for white caps";
    private static final String INVALID_DESCRIPTION_FORMAT = "Invalid pixels (%s)";
    private static final String IMAGINARY_NUMBER_DESCRIPTION = "An imaginary number would have been produced";
    private static final String NEGATIVE_AYS_DESCRIPTION = "Negative value in a_ys spectrum";
    public static final String TURBIDITY_NAME = "turbidity";
    public static final String CONC_GROUPING_PATTERN = "conc";


    private boolean outputKdSpectrum;
    private boolean outputFLH;
    private boolean outputReflectance;
    private String invalidPixelExpression;


    abstract Product createL2WProduct();

    public void setOutputKdSpectrum(boolean outputKdSpectrum) {
        this.outputKdSpectrum = outputKdSpectrum;
    }

    public boolean isOutputKdSpectrum() {
        return outputKdSpectrum;
    }

    public void setOutputFLH(boolean outputFLH) {
        this.outputFLH = outputFLH;
    }

    public boolean isOutputFLH() {
        return outputFLH;
    }

    public void setOutputReflectance(boolean outputReflectance) {
        this.outputReflectance = outputReflectance;
    }

    public boolean isOutputReflectance() {
        return outputReflectance;
    }

    public String getInvalidPixelExpression() {
        return invalidPixelExpression;
    }

    public void setInvalidPixelExpression(String invalidPixelExpression) {
        this.invalidPixelExpression = invalidPixelExpression;
    }

    protected void copyFlagBands(Product source, Product target) {
        ProductUtils.copyFlagBands(source, target);
        final Band[] radiometryBands = source.getBands();
        for (Band band : radiometryBands) {
            if (band.isFlagBand()) {
                final Band targetBand = target.getBand(band.getName());
                targetBand.setSourceImage(band.getSourceImage());
            }
        }
    }

    protected void copyMasks(Product sourceProduct, Product targetProduct) {
        ProductNodeGroup<Mask> maskGroup = sourceProduct.getMaskGroup();
        for (int i = 0; i < maskGroup.getNodeCount(); i++) {
            Mask mask = maskGroup.get(i);
            if (!mask.getImageType().getName().equals(Mask.VectorDataType.TYPE_NAME)) {
                mask.getImageType().transferMask(mask, targetProduct);
            }
        }
    }

    protected void copyReflecBandsIfRequired(Product sourceProduct, Product targetProduct) {
        if (isOutputReflectance()) {
            Band[] bands = sourceProduct.getBands();
            for (Band band : bands) {
                if (band.getName().startsWith("reflec_")) {
                    Band targetBand = ProductUtils.copyBand(band.getName(), sourceProduct, targetProduct);
                    Band sourceBand = sourceProduct.getBand(band.getName());
                    targetBand.setSourceImage(sourceBand.getSourceImage());
                }
            }
            addPatternToAutoGrouping(targetProduct, "reflec");
        }

    }

    protected void renameIops(Product targetProduct) {
        targetProduct.getBand(A_TOTAL_443_SOURCE_NAME).setName(IOP_A_TOTAL_443_NAME);
        targetProduct.getBand(A_YS_443_SOURCE_NAME).setName(IOP_A_YS_443_NAME);
        targetProduct.getBand(A_PIG_443_SOURCE_NAME).setName(IOP_A_PIG_443_NAME);
        Band aPocBand = targetProduct.getBand(A_POC_443_SOURCE_NAME);
        if (aPocBand != null) {
            aPocBand.setName(IOP_A_POC_443_NAME);
        }
        targetProduct.getBand(BB_SPM_443_SOURCE_NAME).setName(IOP_BB_SPM_443_NAME);
        String groupPattern = IOP_PREFIX_TARGET_BAND_NAME.substring(0, IOP_PREFIX_TARGET_BAND_NAME.length() - 1);
        addPatternToAutoGrouping(targetProduct, groupPattern);
    }

    protected void sortFlagBands(Product targetProduct) {
        Band l1_flags = targetProduct.getBand("l1_flags");
        Band l1p_flags = targetProduct.getBand("l1p_flags");
        Band l2r_flags = targetProduct.getBand("l2r_flags");
        targetProduct.removeBand(l1_flags);
        targetProduct.removeBand(l1p_flags);
        targetProduct.removeBand(l2r_flags);
        targetProduct.addBand(l1_flags);
        targetProduct.addBand(l1p_flags);
        targetProduct.addBand(l2r_flags);
    }

    protected void addL2WMasksAndFlags(Product targetProduct) {
        ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();

        FlagCoding l2wFlagCoding = new FlagCoding(L2W_FLAGS_NAME);
        flagCodingGroup.add(l2wFlagCoding);
        l2wFlagCoding.addFlag("C2R_WLR_OOR", 1, WLR_OOR_DESCRIPTION);
        l2wFlagCoding.addFlag("C2R_CONC_OOR", 2, CONC_OOR_DESCRIPTION);
        l2wFlagCoding.addFlag("C2R_OOTR", 4, OOTR_DESCRIPTION);
        l2wFlagCoding.addFlag("C2R_WHITECAPS", 8, WHITE_CAPS_DESCRIPTION);
        l2wFlagCoding.addFlag("QAA_IMAGINARY_NUMBER", 16, IMAGINARY_NUMBER_DESCRIPTION);
        l2wFlagCoding.addFlag("QAA_NEGATIVE_AYS", 32, NEGATIVE_AYS_DESCRIPTION);
        final String invalidDescription = String.format(INVALID_DESCRIPTION_FORMAT, getInvalidPixelExpression());
        l2wFlagCoding.addFlag("INVALID", 128, invalidDescription);

        Band l2wFlagsBand = targetProduct.addBand(L2W_FLAGS_NAME, ProductData.TYPE_UINT8);
        l2wFlagsBand.setSampleCoding(l2wFlagCoding);
        l2wFlagsBand.setDescription("CC L2W water constituents and IOPs retrieval quality flags.");

        ProductNodeGroup<Mask> maskGroup = targetProduct.getMaskGroup();

        addMask(maskGroup, 0, "l2w_cc_c2r_wlr_ootr", WLR_OOR_DESCRIPTION, L2W_FLAGS_NAME + ".C2R_WLR_OOR",
                Color.CYAN, 0.5f);
        addMask(maskGroup, 1, "l2w_cc_c2r_conc_ootr", CONC_OOR_DESCRIPTION, L2W_FLAGS_NAME + ".C2R_CONC_OOR",
                Color.DARK_GRAY, 0.5f);
        addMask(maskGroup, 2, "l2w_cc_c2r_ootr", OOTR_DESCRIPTION, L2W_FLAGS_NAME + ".C2R_OOTR",
                Color.ORANGE, 0.5f);
        addMask(maskGroup, 3, "l2w_cc_c2r_whitecaps", WHITE_CAPS_DESCRIPTION, L2W_FLAGS_NAME + ".C2R_WHITECAPS",
                Color.CYAN, 0.5f);
        addMask(maskGroup, 4, "l2w_cc_qaa_imaginary_number", IMAGINARY_NUMBER_DESCRIPTION,
                L2W_FLAGS_NAME + ".QAA_IMAGINARY_NUMBER",
                Color.MAGENTA, 0.5f);
        addMask(maskGroup, 5, "l2w_cc_qaa_negative_ays", NEGATIVE_AYS_DESCRIPTION, L2W_FLAGS_NAME + ".QAA_NEGATIVE_AYS",
                Color.YELLOW, 0.5f);
        final String invalidMaskDescription = String.format(INVALID_DESCRIPTION_FORMAT, getInvalidMaskExpression());
        addMask(maskGroup, 6, "l2w_cc_invalid", invalidMaskDescription, getInvalidMaskExpression(),
                Color.RED, 0.0f);
    }


    private void addMask(ProductNodeGroup<Mask> maskGroup, int index, String maskName, String maskDescription,
                         String maskExpression, Color maskColor, float transparency) {
        int width = maskGroup.getProduct().getSceneRasterWidth();
        int height = maskGroup.getProduct().getSceneRasterHeight();
        Mask mask = Mask.BandMathsType.create(maskName, maskDescription, width, height,
                                              maskExpression, maskColor, transparency);
        maskGroup.add(index, mask);
    }

    protected void renameConcentrations(Product targetProduct) {
        targetProduct.getBand(TSM_SOURCE_NAME).setName(CONC_TSM_NAME);
        targetProduct.getBand(CHL_CONC_SOURCE_NAME).setName(CONC_CHL_NAME);
        addPatternToAutoGrouping(targetProduct, CONC_GROUPING_PATTERN);
    }


    protected void addFLHBands(Product target) {
        Band flhBand = target.addBand(EXP_FLH_681_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setDescription("Fluorescence line height at 681 nm");
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        flhBand = target.addBand(EXP_FLH_681_NORM_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        flhBand = target.addBand(EXP_FLH_681_ALT_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        flhBand = target.addBand(EXP_FLH_NORM_OLD_681_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setDescription("Fluorescence line height at 681 nm");
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        flhBand = target.addBand(EXP_FLH_ALT_OLD_681_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setDescription("Fluorescence line height at 681 nm");
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        addPatternToAutoGrouping(target, "exp");
    }

    protected void addPatternToAutoGrouping(Product targetProduct, String groupPattern) {
        Product.AutoGrouping autoGrouping = targetProduct.getAutoGrouping();
        String stringPattern = autoGrouping != null ? autoGrouping.toString() + ":" + groupPattern : groupPattern;
        targetProduct.setAutoGrouping(stringPattern);
    }

    private String getInvalidMaskExpression() {
        return getInvalidPixelExpression() + " || l2w_flags.C2R_OOTR || l2w_flags.QAA_IMAGINARY_NUMBER || l2w_flags.QAA_NEGATIVE_AYS";
    }

}
