package org.esa.beam.coastcolour.util;

import com.bc.ceres.core.PrintWriterProgressMonitor;
import ucar.ma2.ArrayByte;
import ucar.ma2.ArrayFloat;
import ucar.ma2.ArrayShort;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.*;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class for product stitching
 * Date: 12.03.12
 * Time: 15:15
 *
 * @author olafd
 */
public class ProductStitcher {

    static final String DIMX_NAME = "x";
    static final String DIMY_NAME = "y";
    static final String TP_DIMX_NAME = "tp_x";
    static final String TP_DIMY_NAME = "tp_y";

    public static final String DATE_PATTERN = "dd-MMM-yyyy HH:mm:ss";

    List<NetcdfFile> ncFileList;

    List<Map<Integer, Long>> bandRowToScanTimeMaps;
    List<Map<Integer, Long>> tpRowToScanTimeMaps;

    List<List<Attribute>> allAttributesLists = new ArrayList<List<Attribute>>();
    List<List<Dimension>> allDimensionsLists = new ArrayList<List<Dimension>>();
    List<List<Variable>> allBandVariablesLists = new ArrayList<List<Variable>>();
    List<List<Variable>> allTpVariablesLists = new ArrayList<List<Variable>>();

    int stitchedProductWidthBands;
    int stitchedProductHeightBands;
    int stitchedProductHeightTps;
    int stitchedProductWidthTps;
    Map<Integer, Long> stitchedProductBandRowToScanTimeMap;
    Map<Integer, Long> stitchedProductTpRowToScanTimeMap;

    public ProductStitcher(List<NetcdfFile> ncFileList) {
        this.ncFileList = ncFileList;

        setAllAttributesList();
        setAllDimensionsList();
        setAllBandVariablesLists();
        setAllTpVariablesLists();
        setRowToScanTimeMaps(true);
        setRowToScanTimeMaps(false);
        setStitchedProductSizeBands();
        setStitchedProductSizeTps();
        setStitchedProductRowToScanTimeMap(false, stitchedProductHeightBands);
        setStitchedProductRowToScanTimeMap(true, stitchedProductHeightTps);
    }


    private void setRowToScanTimeMaps(boolean isTiepoints) {
        List<Map<Integer, Long>> rowToScanTimeMaps = new ArrayList<Map<Integer, Long>>();
        for (NetcdfFile netcdfFile : ncFileList) {
            int yDim = -1;
            int xDim = -1;
            String xDimName = (isTiepoints ? ProductStitcher.TP_DIMX_NAME : ProductStitcher.DIMX_NAME);
            String yDimName = (isTiepoints ? ProductStitcher.TP_DIMY_NAME : ProductStitcher.DIMY_NAME);
            final List<Dimension> dimensions = netcdfFile.getDimensions();
            for (Dimension dimension : dimensions) {
                if (dimension.getName().equals(xDimName)) {
                    xDim = dimension.getLength();
                }
                if (dimension.getName().equals(yDimName)) {
                    yDim = dimension.getLength();
                }
            }
            if (xDim == -1 || yDim == -1) {
                throw new IllegalStateException("Input file ' " + netcdfFile.getLocation() +
                        "' does not have expected dimension names - check product!");
            }

            final List<Attribute> globalAttributes = netcdfFile.getGlobalAttributes();
            long startTime = -1;
            long stopTime = -1;

            for (Attribute attribute : globalAttributes) {
                if (attribute.getName().equals("start_date")) {
                    startTime = getTimeAsLong(attribute);
                }
                if (attribute.getName().equals("stop_date")) {
                    stopTime = getTimeAsLong(attribute);
                }
            }

            if (startTime == -1 || stopTime == -1) {
                throw new IllegalStateException("Input file ' " + netcdfFile.getLocation() +
                        "': start/stop times cannot be parsed - check product!");
            }

            // interpolation:
            Map<Integer, Long> bandRowToScanTimeMap = new HashMap<Integer, Long>();
            for (int i = 0; i < yDim; i++) {
                bandRowToScanTimeMap.put(i, startTime + i * (stopTime - startTime) / (yDim - 1));
            }
            rowToScanTimeMaps.add(bandRowToScanTimeMap);
        }

        if (isTiepoints) {
            tpRowToScanTimeMaps = rowToScanTimeMaps;
        } else {
            bandRowToScanTimeMaps = rowToScanTimeMaps;
        }
    }

    private void setAllAttributesList() {
        for (NetcdfFile ncFile : ncFileList) {
            final List<Attribute> attributes = ncFile.getGlobalAttributes();
            allAttributesLists.add(attributes);
        }
    }

    private void setAllDimensionsList() {
        for (NetcdfFile ncFile : ncFileList) {
            final List<Dimension> dimensions = ncFile.getDimensions();
            allDimensionsLists.add(dimensions);
        }
    }

    private void setAllBandVariablesLists() {
        for (NetcdfFile ncFile : ncFileList) {
            final List<Variable> allVariablesList = ncFile.getVariables();
            List<Variable> bandVariablesList = new ArrayList<Variable>();
            for (Variable variable : allVariablesList) {
                if (ProductStitcherValidation.isValidBandVariable(variable) || ProductStitcherValidation.isValidFlagBandVariable(variable)) {
                    bandVariablesList.add(variable);
                }
            }
            allBandVariablesLists.add(bandVariablesList);
        }
    }

    private void setAllTpVariablesLists() {
        for (NetcdfFile ncFile : ncFileList) {
            final List<Variable> allVariablesList = ncFile.getVariables();
            List<Variable> tpVariablesList = new ArrayList<Variable>();
            for (Variable variable : allVariablesList) {
                // todo: this is bad. validate as for bands
                if (variable.getDimensions().size() == 2 && variable.getDataType().getClassType().getSimpleName().equals("float") &&
                        variable.getDimension(0).getName().equals(TP_DIMY_NAME) && variable.getDimension(1).getName().equals(TP_DIMX_NAME)) {
                    tpVariablesList.add(variable);
                }
            }
            allTpVariablesLists.add(tpVariablesList);
        }
    }

    public void setStitchedProductRowToScanTimeMap(boolean isTiepoints, int yDim) {

        NetcdfFile firstNcFile = ncFileList.get(0);
        NetcdfFile lastNcFile = ncFileList.get(ncFileList.size() - 1);

        final List<Attribute> firstGlobalAttributes = firstNcFile.getGlobalAttributes();
        long startTime = -1;
        for (Attribute attribute : firstGlobalAttributes) {
            if (attribute.getName().equals("start_date")) {
                startTime = getTimeAsLong(attribute);
            }
        }

        final List<Attribute> lastGlobalAttributes = lastNcFile.getGlobalAttributes();
        long stopTime = -1;
        for (Attribute attribute : lastGlobalAttributes) {
            if (attribute.getName().equals("stop_date")) {
                stopTime = getTimeAsLong(attribute);
            }
        }

        if (startTime == -1) {
            throw new IllegalStateException("Input file ' " + firstNcFile.getLocation() +
                    "': start time cannot be parsed - check product!");
        }
        if (stopTime == -1) {
            throw new IllegalStateException("Input file ' " + lastNcFile.getLocation() +
                    "': stop time cannot be parsed - check product!");
        }

        // interpolation:
        Map<Integer, Long> stitchedProductRowToScanTimeMap = new HashMap<Integer, Long>();
        for (int i = 0; i < yDim; i++) {
            stitchedProductRowToScanTimeMap.put(i, startTime + i * (stopTime - startTime) / (yDim - 1));
        }
        if (isTiepoints) {
            stitchedProductTpRowToScanTimeMap = stitchedProductRowToScanTimeMap;
        } else {
            stitchedProductBandRowToScanTimeMap = stitchedProductRowToScanTimeMap;
        }
    }

    private static long getTimeAsLong(Attribute attribute) {
        String dateString = attribute.getStringValue();
        try {
            return ProductStitcherNetcdfUtils.parse(dateString, DATE_PATTERN);
        } catch (ParseException e) {
            // todo
            e.printStackTrace();
        }
        return -1;
    }

    public void setStitchedProductSizeBands() {
        // go through row <--> scanTime
        for (int i = 0; i < bandRowToScanTimeMaps.size() - 1; i++) {
            final Map<Integer, Long> map = bandRowToScanTimeMaps.get(i);
            final Map<Integer, Long> nextMap = bandRowToScanTimeMaps.get(i + 1);
            // count until start time of next product is reached
            int j = 0;
            while (map.get(j++) < nextMap.get(0)) {
                stitchedProductHeightBands++;
            }
        }
        stitchedProductHeightBands += bandRowToScanTimeMaps.get(bandRowToScanTimeMaps.size() - 1).size();
        stitchedProductWidthBands = allDimensionsLists.get(0).get(1).getLength();
    }

    public void setStitchedProductSizeTps() {
        // go through row <--> scanTime
        for (int i = 0; i < tpRowToScanTimeMaps.size() - 1; i++) {
            final Map<Integer, Long> map = tpRowToScanTimeMaps.get(i);
            final Map<Integer, Long> nextMap = tpRowToScanTimeMaps.get(i + 1);
            int j = 0;
            // count until start time of next product is reached
            while (map.get(j++) < nextMap.get(0)) {
                stitchedProductHeightTps++;
            }
        }
        stitchedProductHeightTps += tpRowToScanTimeMaps.get(tpRowToScanTimeMaps.size() - 1).size();
        stitchedProductWidthTps = allDimensionsLists.get(0).get(3).getLength();
    }


    public void writeStitchedProduct(File ncResultFile,
                                     DefaultErrorHandler handler) {
        NetcdfFileWriteable outFile = null;
        final PrintWriterProgressMonitor pm = new PrintWriterProgressMonitor(System.out);
        pm.beginTask( "Writing stitched product...", 0);
        try {
            outFile = NetcdfFileWriteable.createNew(ncResultFile.getAbsolutePath(), false);

            // add global attributes from first product, exchange specific single attributes:
            addGlobalAttributes(allAttributesLists, outFile);

            // add dimensions to output: we have y, x, tp_y, tp_x in this sequence:
            final Dimension yDim = allDimensionsLists.get(0).get(0);
            final Dimension xDim = allDimensionsLists.get(0).get(1);
            final Dimension yTpDim = allDimensionsLists.get(0).get(2);
            final Dimension xTpDim = allDimensionsLists.get(0).get(3);
            addDimensions(outFile, yDim, xDim, yTpDim, xTpDim);

            // add bands and tie point variable attributes to output:
            addVariableAttributes(allBandVariablesLists, outFile, yDim, xDim);
            addVariableAttributes(allTpVariablesLists, outFile, yTpDim, xTpDim);

            // we need to call 'create' after all attributes and dimensions were added:
            try {
                // try in standard mode first, which may fail for large files...
                outFile.create();
            } catch (IllegalArgumentException e) {
                Logger.getAnonymousLogger().log(Level.INFO, "Switching to NetCDF 'large file' mode...");
                outFile.setLargeFile(true);
                outFile.create();
            }

            // add band and tie point data to output:
            writeVariables(allBandVariablesLists, bandRowToScanTimeMaps, outFile, false, pm);
            writeVariables(allTpVariablesLists, tpRowToScanTimeMaps, outFile, true, pm);

        } catch (IOException e) {
            handler.error(e);
        } catch (InvalidRangeException e) {
            handler.error(e);
        } finally {
            if (null != outFile)
                try {
                    outFile.close();
                } catch (IOException ignore) {
                }
            pm.done();
        }
        Logger.getAnonymousLogger().log(Level.INFO, "Finished writing stitched product.");
    }

    private static void addGlobalAttributes(List<List<Attribute>> allAttributesLists, NetcdfFileWriteable outFile) {
        final List<Attribute> firstProductGlobalAttributes = allAttributesLists.get(0);
        for (Attribute attribute : firstProductGlobalAttributes) {
            outFile.addGlobalAttribute(attribute);
        }
        final List<Attribute> lastProductGlobalAttributes = allAttributesLists.get(allAttributesLists.size() - 1);
        for (Attribute attribute : lastProductGlobalAttributes) {
            if (attribute.getName().equals("stop_date")) {
                outFile.addGlobalAttribute(attribute);
            }
        }
    }

    private void addDimensions(NetcdfFileWriteable outFile, Dimension yDim, Dimension xDim, Dimension yTpDim, Dimension xTpDim) {
        yDim.setLength(stitchedProductHeightBands);
        xDim.setLength(stitchedProductWidthBands);
        outFile.addDimension(DIMY_NAME, yDim.getLength());
        outFile.addDimension(DIMX_NAME, xDim.getLength());
        yTpDim.setLength(stitchedProductHeightTps);
        xTpDim.setLength(stitchedProductWidthTps);
        outFile.addDimension(TP_DIMY_NAME, yTpDim.getLength());
        outFile.addDimension(TP_DIMX_NAME, xTpDim.getLength());
        outFile.addGlobalAttribute("TileSize", yDim.getLength() + ":" + xDim.getLength());
    }

    private static void addVariableAttributes(List<List<Variable>> variableLists,
                                              NetcdfFileWriteable outFile,
                                              Dimension yDim, Dimension xDim) throws IOException, InvalidRangeException {
        List<Variable> firstVariables = variableLists.get(0);
        // loop over variables
        for (Variable variable : firstVariables) {
            // add band variables, take from first product
            outFile.addVariable(variable.getName(), variable.getDataType(), new Dimension[]{yDim, xDim});
            final List<Attribute> variableAttributes = variable.getAttributes();
            for (Attribute attribute : variableAttributes) {
                outFile.addVariableAttribute(variable.getName(), attribute);
            }
        }
    }

    private void writeVariables(List<List<Variable>> variableLists,
                                List<Map<Integer, Long>> rowToScanTimeMaps,
                                NetcdfFileWriteable outFile,
                                boolean isTiepoints, PrintWriterProgressMonitor pm) throws IOException, InvalidRangeException {

        int width = (isTiepoints ? stitchedProductWidthTps : stitchedProductWidthBands);
        int height = (isTiepoints ? stitchedProductHeightTps : stitchedProductHeightBands);

        // set up data buffers for all types which occur in L1P, L2R, L2W products
        ArrayFloat.D2 bandDataFloat = new ArrayFloat.D2(height, width);
        ArrayShort.D2 bandDataShort = new ArrayShort.D2(height, width);
        ArrayByte.D2 bandDataByte = new ArrayByte.D2(height, width);

        List<Variable> firstProductBandVariables = variableLists.get(0);

        for (Variable variable : firstProductBandVariables) {
            // loop over single products
            System.out.println("...writing variable '" + variable.getName() + "'...");
            for (int i = 0; i < variableLists.size(); i++) {
                List<Variable> allBandVariables = variableLists.get(i);
                for (Variable variable2 : allBandVariables) {
                    if (variable2.getName().equals(variable.getName())) {
                        if (variable.getName().equals("metadata")) {
                            ArrayByte.D1 metadataBuffer = new ArrayByte.D1(1);
                            metadataBuffer.set(0, variable2.readScalarByte());
                        } else {
                            // get data array for THIS variable and THIS single product
                            // todo: check if and why we need this?!
                            variable2.getDimension(0).setLength(variable2.getShape(0));
                            variable2.getDimension(1).setLength(variable2.getShape(1));
                            ////

                            byte[][] byteVals = null;
                            short[][] shortVals = null;
                            float[][] floatVals = null;

                            switch (variable2.getDataType()) {
                                case BYTE:
                                    byteVals = ProductStitcherNetcdfUtils.getByte2DArrayFromNetcdfVariable(variable2);
                                    break;
                                case SHORT:
                                    shortVals = ProductStitcherNetcdfUtils.getShort2DArrayFromNetcdfVariable(variable2);
                                    break;
                                case FLOAT:
                                    floatVals = ProductStitcherNetcdfUtils.getFloat2DArrayFromNetcdfVariable(variable2);
                                    break;
                                default:
                                    throw new IllegalArgumentException("Data type '" + variable.getDataType().name() + "' not supported.");
                            }
                            int sourceProductIndexPrev = 0;
                            int valuesRowIndex = 0;
                            // now loop over ALL rows:
                            for (int j = 0; j < height; j++) {
                                // search the right single product by row time
                                int sourceProductIndex = getSourceProductIndex(rowToScanTimeMaps, j, isTiepoints);

                                if (sourceProductIndex < 0 || sourceProductIndex > ncFileList.size()) {
                                    throw new IllegalStateException("Unknown status of source product start/stop times - cannot continue.");
                                }

                                if (sourceProductIndex > sourceProductIndexPrev) {
                                    valuesRowIndex = 0;
                                }

                                // if the current single product is the right one, loop over raster and set netcdf floatVals
                                // todo: we need to interpolate for the tie points because of the mismatch of the single products
                                if (sourceProductIndex == i) {
//                                    System.out.println("i,j,valuesRowIndex,yDim = " + i + "," + j + "," + valuesRowIndex + "," +
//                                    variable2.getShape()[0]);
                                    for (int k = 0; k < width; k++) {
                                        switch (variable2.getDataType()) {
                                            case BYTE:
                                                bandDataByte.set(j, k, byteVals[valuesRowIndex][k]);
                                                break;
                                            case SHORT:
                                                bandDataShort.set(j, k, shortVals[valuesRowIndex][k]);
                                                break;
                                            case FLOAT:
                                                bandDataFloat.set(j, k, floatVals[valuesRowIndex][k]);
                                                break;
                                            default:
                                                throw new IllegalArgumentException("Data type '" + variable2.getDataType().name() + "' not supported.");
                                        }
                                    }
                                }
                                valuesRowIndex++;
                                sourceProductIndexPrev = sourceProductIndex;
                            }
                            switch (variable2.getDataType()) {
                                case BYTE:
                                    outFile.write(variable2.getName(), bandDataByte);
                                    break;
                                case SHORT:
                                    outFile.write(variable2.getName(), bandDataShort);
                                    break;
                                case FLOAT:
                                    outFile.write(variable2.getName(), bandDataFloat);
                                    break;
                                default:
                                    throw new IllegalArgumentException("Data type '" + variable2.getDataType().name() + "' not supported.");
                            }
                        }
                    }
                }
            }
        }
    }

    private int getSourceProductIndex(List<Map<Integer, Long>> rowToScanTimeMaps, int rowIndex,
                                      boolean isTiepoints) {
        int sourceProductIndex = -1;
        Map<Integer, Long> stitchedProductRowToScanTimeMap = new HashMap<Integer, Long>();
        if (isTiepoints) {
            stitchedProductRowToScanTimeMap = stitchedProductTpRowToScanTimeMap;
        } else {
            stitchedProductRowToScanTimeMap = stitchedProductBandRowToScanTimeMap;
        }
        long sourceProductTime = stitchedProductRowToScanTimeMap.get(rowIndex);
        for (int k = rowToScanTimeMaps.size() - 1; k >= 0; k--) {
            Map<Integer, Long> map = rowToScanTimeMaps.get(k);
            long startTime = map.get(0);
            long stopTime = map.get(map.size() - 1);
            if (startTime <= sourceProductTime && sourceProductTime <= stopTime) {
                sourceProductIndex = k;
                break;
            }
        }

        return sourceProductIndex;
    }
}