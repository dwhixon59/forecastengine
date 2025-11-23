package com.hixon.financialApp.controller;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating and managing import strategy instances.
 *
 * <p>This factory provides centralized access to all available import strategies
 * and helps select the appropriate strategy based on file type or format.</p>
 *
 * <h3>Supported Formats:</h3>
 * <ul>
 *   <li><b>CSV</b> - Comma/Tab-Separated Values (.csv, .tsv, .txt)</li>
 *   <li><b>QFX</b> - Quicken Web Connect (.qfx) - XML-based OFX 2.x</li>
 *   <li><b>OFX</b> - Open Financial Exchange (.ofx) - SGML or XML</li>
 *   <li><b>QIF</b> - Quicken Interchange Format (.qif) - Legacy text format</li>
 * </ul>
 *
 * <h3>Usage Examples:</h3>
 * <pre>
 * // Get strategy by file extension
 * ImportStrategy strategy = ImportStrategyFactory.getStrategyForFile("transactions.qfx");
 *
 * // Get all available strategies
 * List&lt;ImportStrategy&gt; all = ImportStrategyFactory.getAllStrategies();
 *
 * // Get specific strategy by name
 * ImportStrategy csv = ImportStrategyFactory.getCsvStrategy();
 * </pre>
 *
 * <h3>Adding New Formats:</h3>
 * <p>To add support for a new format:</p>
 * <ol>
 *   <li>Create a new class implementing {@link ImportStrategy}</li>
 *   <li>Add an instance to {@link #getAllStrategies()}</li>
 *   <li>Optionally add a convenience getter method</li>
 * </ol>
 *
 * @author David Hixon
 * @version 1.0
 * @since 2025-11-23
 */
public class ImportStrategyFactory {

    // Singleton instances of each strategy
    private static final CsvImportStrategy CSV_STRATEGY = new CsvImportStrategy();
    private static final QfxImportStrategy QFX_STRATEGY = new QfxImportStrategy();
    private static final OfxImportStrategy OFX_STRATEGY = new OfxImportStrategy();
    private static final QifImportStrategy QIF_STRATEGY = new QifImportStrategy();

    /**
     * Private constructor to prevent instantiation of factory class.
     */
    private ImportStrategyFactory() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets all available import strategies.
     *
     * @return List of all registered import strategies
     */
    public static List<ImportStrategy> getAllStrategies() {
        List<ImportStrategy> strategies = new ArrayList<>();
        strategies.add(CSV_STRATEGY);
        strategies.add(QFX_STRATEGY);
        strategies.add(OFX_STRATEGY);
        strategies.add(QIF_STRATEGY);
        return strategies;
    }

    /**
     * Gets the appropriate import strategy for a given filename.
     *
     * <p>This method examines the file extension and returns the first strategy
     * that can parse the file. If no strategy is found, returns CSV as default.</p>
     *
     * @param filename The filename to check (e.g., "transactions.qfx")
     * @return The appropriate import strategy, or CSV strategy as default
     */
    public static ImportStrategy getStrategyForFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return CSV_STRATEGY;  // Default to CSV
        }

        // Try each strategy's canParse method
        for (ImportStrategy strategy : getAllStrategies()) {
            if (strategy.canParse(filename)) {
                return strategy;
            }
        }

        // Default to CSV if no specific strategy found
        return CSV_STRATEGY;
    }

    /**
     * Gets the appropriate import strategy by file extension.
     *
     * @param extension The file extension without dot (e.g., "qfx", "csv")
     * @return The appropriate import strategy, or null if not found
     */
    public static ImportStrategy getStrategyByExtension(String extension) {
        if (extension == null) {
            return null;
        }

        String ext = extension.toLowerCase().replace(".", "");

        for (ImportStrategy strategy : getAllStrategies()) {
            for (String supportedExt : strategy.getSupportedExtensions()) {
                if (supportedExt.equalsIgnoreCase(ext)) {
                    return strategy;
                }
            }
        }

        return null;
    }

    /**
     * Gets the CSV import strategy instance.
     *
     * @return The CSV import strategy
     */
    public static CsvImportStrategy getCsvStrategy() {
        return CSV_STRATEGY;
    }

    /**
     * Gets the QFX (Quicken Web Connect) import strategy instance.
     *
     * @return The QFX import strategy
     */
    public static QfxImportStrategy getQfxStrategy() {
        return QFX_STRATEGY;
    }

    /**
     * Gets the OFX (Open Financial Exchange) import strategy instance.
     *
     * @return The OFX import strategy
     */
    public static OfxImportStrategy getOfxStrategy() {
        return OFX_STRATEGY;
    }

    /**
     * Gets the QIF (Quicken Interchange Format) import strategy instance.
     *
     * @return The QIF import strategy
     */
    public static QifImportStrategy getQifStrategy() {
        return QIF_STRATEGY;
    }

    /**
     * Gets a formatted string listing all supported file formats.
     *
     * @return Human-readable list of supported formats
     */
    public static String getSupportedFormatsDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Supported import formats:\n");

        for (ImportStrategy strategy : getAllStrategies()) {
            sb.append("  • ").append(strategy.getStrategyName()).append(" (");
            String[] extensions = strategy.getSupportedExtensions();
            for (int i = 0; i < extensions.length; i++) {
                sb.append(".").append(extensions[i]);
                if (i < extensions.length - 1) {
                    sb.append(", ");
                }
            }
            sb.append(")\n");
        }

        return sb.toString();
    }
}

