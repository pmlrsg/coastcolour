package org.esa.beam.coastcolour.util;

import ucar.nc2.Variable;

/**
 * Validation class for CC product stitcher
 * Date: 15.03.12
 * Time: 11:33
 *
 * @author olafd
 */
public class ProductStitcherValidation {
    static boolean isValidBandVariable(Variable variable) {
        return variable.getName().equals("metadata") ||
                isValidL1PBandVariable(variable) ||
                isValidL2RBandVariable(variable) ||
                isValidL2WBandVariable(variable);
    }

    static boolean isValidFlagBandVariable(Variable variable) {
        return isValidL1PFlagBandVariable(variable) ||
                isValidL2RFlagBandVariable(variable) ||
                isValidL2WFlagBandVariable(variable);
    }


    private static boolean isValidL2WBandVariable(Variable variable) {
        final String name = variable.getName();
        final boolean isNameValid = name.equals("lat") || name.equals("lon") ||
                name.equals("chiSquare") || name.equals("K_min") || name.equals("Z90_max") || name.equals("turbidity") ||
                name.startsWith("iop") || name.startsWith("conc") || name.startsWith("Kd");
        return isNameValid && areDimensionsValid(variable);
    }

    private static boolean isValidL2RBandVariable(Variable variable) {
        final String name = variable.getName();
        final boolean isNameValid = name.equals("lat") || name.equals("lon") || name.equals("ang_443_865") ||
                name.startsWith("reflec") || name.startsWith("norm_refl") || name.startsWith("atm_tau");
        return isNameValid && areDimensionsValid(variable);
    }

    private static boolean isValidL1PBandVariable(Variable variable) {
        final String name = variable.getName();
        final boolean isNameValid = name.equals("lat") || name.equals("lon") || name.startsWith("radiance");
        return isNameValid && areDimensionsValid(variable);
    }

    private static boolean areDimensionsValid(Variable variable) {
        return variable.getDimensions().size() == 2 &&
                variable.getDimension(0).getName().equals(ProductStitcher.DIMY_NAME) &&
                variable.getDimension(1).getName().equals(ProductStitcher.DIMX_NAME);
    }

    private static boolean isValidL2WFlagBandVariable(Variable variable) {
        final boolean isNameValid = isValidL2RFlagBandVariable(variable) || variable.getName().equals("l2w_flags");
        return isNameValid;
    }

    private static boolean isValidL2RFlagBandVariable(Variable variable) {
        final boolean isNameValid = isValidL1PFlagBandVariable(variable) || variable.getName().equals("l2r_flags");
        return isNameValid && areDimensionsValid(variable);
    }

    private static boolean isValidL1PFlagBandVariable(Variable variable) {
        final boolean isNameValid = variable.getName().equals("l1_flags") || variable.getName().equals("l1p_flags");
        return isNameValid && areDimensionsValid(variable);
    }

}
