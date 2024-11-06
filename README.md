# Financial App

## Overview

This project is a financial application that helps users manage their transactions and accounts. It includes features for importing transactions, logging import events, and categorizing transactions.

## Attributions
City and state data used in this application are provided by SimpleMaps.
Visit: https://simplemaps.com/data/us-cities

## Technologies Used

- **Java**: The primary programming language used for the application.
- **Maven**: Used for project management and build automation.

## Project Structure

- `src/main/java/com/hixon/financialApp/controller/`: Contains the controller classes for handling the business logic.
    - `WellsFargoBankController.java`: Handles specific logic for Wells Fargo bank transactions.
    - `ImportLog.java`: Manages the logging of imported transactions.
- `src/main/java/com/hixon/financialApp/model/`: Contains the model classes representing the application's data.
- `src/main/java/com/hixon/financialApp/utility/`: Contains utility classes and methods used across the application.

## How to Build

To build the project, use Maven:

```sh
mvn clean install

