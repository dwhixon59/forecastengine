    /**
     * Select from a numbered list using an enum with a default value.
     * Supports help via 'h' followed by a number (e.g., 'h 3' for help on option 3).
     */
    protected <T extends Enum<T>> T selectFromNumberedList(String prompt, T defaultValue, Class<T> enumType)
            throws CancelException, QuitException, SkipException {
        T[] values = enumType.getEnumConstants();
        List<String> options = new ArrayList<>();
        for (T value : values) {
            options.add(value.toString());
        }

        int defaultIndex = defaultValue != null ? defaultValue.ordinal() : -1;

        sayH3(prompt);
        int i = 1;
        for (String option : options) {
            say("\t" + i++ + " - " + option);
        }
        say("\tEnter 'h' followed by a number (e.g., 'h 3') for help on a specific option.");

        String optionPrompt = "Select an option";
        if (defaultValue != null) {
            optionPrompt += " [" + defaultValue.toString() + "]";
        }
        optionPrompt += " (1 to " + (i - 1) + "): ";

        while (true) {
            ask(optionPrompt);
            String response = getLine().trim();

            if (response.isEmpty() && defaultValue != null) {
                return defaultValue;
            }

            // Check for help request: 'h' or 'H' followed by optional whitespace and a number
            if (response.toLowerCase().matches("^h\\s*\\d+$")) {
                try {
                    // Extract the number after 'h' and optional whitespace
                    String numberPart = response.toLowerCase().replaceFirst("^h\\s*", "");
                    int helpIndex = Integer.parseInt(numberPart);

                    if (helpIndex >= 1 && helpIndex <= values.length) {
                        T enumValue = values[helpIndex - 1];
                        String enumClassName = enumType.getSimpleName().toLowerCase();
                        String enumValueName = enumValue.name().toLowerCase();
                        String helpKey = enumClassName + "." + enumValueName;

                        // Load help text from properties file
                        try (InputStream input = getClass().getClassLoader()
                                .getResourceAsStream("help-text.properties")) {
                            if (input != null) {
                                Properties helpProperties = new Properties();
                                helpProperties.load(input);
                                String helpText = helpProperties.getProperty(helpKey);

                                if (helpText != null && !helpText.trim().isEmpty()) {
                                    say("\nHelp for " + enumValue.toString() + ":");
                                    say(helpText);
                                    say();
                                } else {
                                    say("No help available for " + enumValue.toString() + " (key: " + helpKey + ").");
                                }
                            } else {
                                say("Help text file not found.");
                            }
                        } catch (IOException e) {
                            say("Error loading help text: " + e.getMessage());
                        }
                    } else {
                        say("Please enter a help number between 1 and " + values.length + " (e.g., 'h " + Math.min(3, values.length) + "').");
                    }
                    continue; // Stay in the loop for another selection
                } catch (NumberFormatException e) {
                    say("Invalid help format. Use 'h' followed by a number (e.g., 'h 3').");
                    continue;
                }
            }

            try {
                int selection = Integer.parseInt(response);
                if (selection >= 1 && selection <= values.length) {
                    return values[selection - 1];
                } else {
                    say("Please enter a number between 1 and " + values.length + ".");
                }
            } catch (NumberFormatException e) {
                say("Invalid input. Please enter a number, or 'h' followed by a number for help.");
            }
        }
    }
