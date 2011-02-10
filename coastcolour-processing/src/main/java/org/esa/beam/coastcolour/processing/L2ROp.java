package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;

import java.util.HashMap;

@OperatorMetadata(alias = "CoastColour.L2R")
public class L2ROp extends Operator {

    @SourceProduct(description = "MERIS L1B or L1P product")
    private Product sourceProduct;

    @Override
    public void initialize() throws OperatorException {
        Product sourceProduct = this.sourceProduct;
        if (!isL1PSourceProduct(sourceProduct)) {
            sourceProduct = GPF.createProduct("CoastColour.L1P", GPF.NO_PARAMS, sourceProduct);
        }

        HashMap<String, Product> sourceProducts = new HashMap<String, Product>();
        sourceProducts.put("merisProduct", sourceProduct);

        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("doSmileCorrection", false);
        parameters.put("outputTosa", false);
        parameters.put("outputReflec", true);
        parameters.put("outputPath", false);
        parameters.put("outputTransmittance", false);
        parameters.put("deriveRwFromPath", false);
        parameters.put("landExpression", "toa_reflec_10 > toa_reflec_6 AND toa_reflec_13 > 0.0475");
        parameters.put("cloudIceExpression", "toa_reflec_14 > 0.2");
        parameters.put("useFlint", false);

        Product targetProduct = GPF.createProduct("Meris.GlintCorrection", parameters, sourceProducts);
        setTargetProduct(targetProduct);
    }

    private boolean isL1PSourceProduct(Product sourceProduct) {
        return sourceProduct.containsBand("l1p_flags");
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(L2ROp.class);
        }
    }
}