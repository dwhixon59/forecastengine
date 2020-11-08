package com.hixon.financialApp.model.register;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.utility.Utility;
import org.apache.commons.csv.CSVRecord;

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/*
 * This class analyzes transactions from a Wells Fargo CSV download file:
 */
public class WellsFargoBank extends Bank {

   /*
    * Fields in the Wells Fargo download file transaction classifier:
    */
   //TODO:  Had to remove UT because it clashed with the abbreviation for "utilities".
   private static String states = "|AL|AK|AS|AZ|AR|CA|CO|CT|DE|DC|FM|FL|GA|GU|HI|ID|IL|IN|IA|KS|KY|LA|ME|MH|MD|MA|MI|" +
           "MN|MS|MO|MT|NE|NV|NH|NJ|NM|NY|NC|ND|MP|OH|OK|OR|PW|PA|PR|RI|SC|SD|TN|TX|VT|VA|WA|WV|WI|WY|";
   private final SimpleDateFormat sdf = new SimpleDateFormat("M/dd/yyyy", Locale.ENGLISH);
   private String[] payeeTokens;


   /*
    * Getters and setters for the Wells Fargo download file transaction classifier:
    */


   /*
    * Constructors for the Wells Fargo download file transaction classifier:
    */
   public WellsFargoBank(Register register) {

      super(register, Utility.getResolver());
   }


   /*
    * Helper methods:
    */
   @Override
   public String getRegisterImportRecordBaseName(CSVRecord record) throws ParseException {
      return record.get(Transaction.Headers.TRANSACTION_DATE) + "\t" + record.get(Transaction.Headers.AMOUNT) + "\t" +
              record.get(Transaction.Headers.CLEARED) + "\t" + record.get(Transaction.Headers.CHECK_NUMBER) + "\t" +
              record.get(Transaction.Headers.PAYEE);
   }


   /*
    * Main methods for the Wells Fargo download file transaction classifier:
    */
   // Load up a Transaction from a Wells Fargo CSV transaction download file:
   @Override
   public Transaction createFromCSVRecord(CSVRecord record, String importRecordId) throws ParseException, RegisterException,
           SQLException {

      Transaction transaction = new Transaction(register);
      loadFromCsvRecord(record, importRecordId, transaction);
      return transaction;
   }

   // Load a transaction from a posted transaction CSV record:
   public void loadFromCsvRecord(CSVRecord record, String importRecordId, Transaction transaction) throws ParseException,
           RegisterException, SQLException {

      // Set the fields of the transaction from the tokens in the record:
      Calendar postDate = Calendar.getInstance();
      postDate.setTime(sdf.parse(record.get(Transaction.Headers.TRANSACTION_DATE)));
      transaction.setPostDate(postDate);
      transaction.setPayee(record.get(Transaction.Headers.PAYEE));
      transaction.setAmount(Double.parseDouble(record.get(Transaction.Headers.AMOUNT)));
      transaction.setCleared(record.get(Transaction.Headers.CLEARED).contentEquals("*"));
      String checkNumberString = record.get(Transaction.Headers.CHECK_NUMBER);
      if (checkNumberString != null && checkNumberString.length() > 0) {
         transaction.setCheckNumber(Integer.parseInt(record.get(Transaction.Headers.CHECK_NUMBER)));
      } else {
         transaction.setCheckNumber(0);
      }
      transaction.setImportRecordId(importRecordId);

      // Tokenize the bank payee (single blank is the separator):
      payeeTokens = transaction.getPayee().split(" ");

      // Locate the authorization date if there is one:
      int i = 2;
      for (; i < payeeTokens.length; i++) {

         // If we found a date, use it as the authorization date:
         if (payeeTokens[i].matches("^(0?[1-9]|1[0-2]){1}\\/(0?[1-9]|1[0-9]|2[0-9]|3[0-1]){1}.*")) {
            transaction.setAuthorizationDate(Utility.stringDateSlashToCalendarDate(payeeTokens[i]));
            break;
         }
      }

      // Parse out the merchant name:
      transaction.setMerchantPayee(parseMerchantPayee(transaction.getPayee()));

      transaction.setDirty(true);
   }

   @Override
   public Transaction getMatchingProvisionalTransaction(CSVRecord record, Merchant merchant) throws RegisterException,
           SQLException,
           EntityException {
      return Transaction.getFirstProvisionalTransaction(merchant.getId(),
              Double.parseDouble(record.get(Transaction.Headers.AMOUNT)));
   }

   // Load a transaction from a CSV provisional transaction record:
   @Override
   public Transaction loadProvisionalTransactionFromCSV(String line, Register register) throws ParseException,
           SQLException, RegisterException {
      String[] tokens;

      // If the user didn't pick up the tab character when they copied the list, add one:
      if (line.getBytes()[0] != '\t') line = "\t" + line;

      // Split the line.  If we don't get at least three tokens, then this isn't a valid line:
      tokens = line.split("\t");
      if (tokens.length < 3) {
         throw new ParseException("Too few tokens in the line.", 0);
      }

      // Figure out which merchant the transaction is associated with:
      payeeTokens = tokens[2].split(" ");
      String merchantPayee = parseMerchantPayee(tokens[2]);

      // Make sure there are both a credit and debit for the Transaction constructor:
      String credit;
      if (tokens.length < 4) {
         credit = "0.00";
      } else {
         credit = tokens[3];
      }
      String debit;
      if (tokens.length < 5) {
         debit = "0.00";
      } else
      {
         debit = tokens[4];
      }

      // Create a transaction based on the provisional record:
      return new Transaction(register, tokens[1], tokens[2], credit, debit, merchantPayee);
   }

   // Parse out the merchant name from a Wells Fargo CSV transaction download file:
   public String parseMerchantPayee(String payee) throws RegisterException, SQLException {

      // Construct the merchant payee string from portions of the bank payee string:
      String merchantPayee;
      String firstFewWords;
      payeeTokens = payee.split(" ");
      if (payeeTokens[0].equalsIgnoreCase("CHECK")) {
         firstFewWords = payeeTokens[0];
      } else {
         if (payeeTokens.length >= 3) {
            firstFewWords = payeeTokens[0] + " " + payeeTokens[1] + " " + payeeTokens[2];
         } else {
            firstFewWords = payeeTokens[0] + " " + payeeTokens[1];
         }
      }

      int i;
      switch (firstFewWords) {

         // If the transaction is a purchase:
         case "PURCHASE AUTHORIZED ON":
         case "PURCHASE RETURN AUTHORIZED":
         case "PURCHASE WITH CASH":
         case "RECURRING PAYMENT AUTHORIZED":

            // Beginning with the token after the date of the transaction, skip over any tokens starting with a digit:
            int start = 0;
            switch (payeeTokens[2]) {
               case "ON":
                  start = 4;
                  break;
               case "AUTHORIZED":
                  start = 5;
                  break;
               case "CASH":
                  start = 9;
                  break;
            }

            // Derive a payee from the remaining tokens:
            merchantPayee = makePayeeFromTokens(start);
            break;

         // If this is a transfer:
         case "ONLINE TRANSFER TO":
         case "ONLINE TRANSFER FROM":
         case "RECURRING TRANSFER TO":
         case "RECURRING TRANSFER FROM":
         case "ATM TRANSFER AUTHORIZED":
         case "Transfer in Branch/Store":

            // Find the account number:
            //noinspection StatementWithEmptyBody
            for (i = 0; i < payeeTokens.length && !payeeTokens[i].matches("^XXXX[X]*[0-9]{4}"); i++) ;
            String accountNumber;
            if (i == payeeTokens.length) {
               accountNumber = resolver.resolveUnmatchedAccount(payee);
            } else {
               accountNumber = payeeTokens[i];
            }

            // then if the transfer is to a register that is part of the forecast then the action is transparent
            // to the budget:
            String lastFourDigits = accountNumber.substring(accountNumber.length() - 4);
            Register register = Register.getByLastFourDigits(lastFourDigits);
            String toFrom;
            if (payeeTokens[0].equalsIgnoreCase("ATM")) {
               toFrom = (payeeTokens[5].equalsIgnoreCase("TO")) ? "to" : "from";
            } else {
               toFrom = (payeeTokens[2].equalsIgnoreCase("TO")) ? "to" : "from";
            }
            if (register != null) {
               merchantPayee = "Transfer " + toFrom + " account " + register.getRegisterName();
            } else {
               merchantPayee = "Transfer " + toFrom + " account " + accountNumber;
            }
            break;

         // If this is an interest payment:
         case "INTEREST PAYMENT":

            // then the merchant is Interest Payment:
            merchantPayee = "Interest Payment";
            break;

         // Overdraft fees:
         case "OVERDRAFT FEE FOR":

            // then Wells Fargo is the merchant:
            merchantPayee = "Overdraft Fee";
            break;

         case "ATM CASH DEPOSIT":
            merchantPayee = "ATM Cash Deposit";
            break;

         case "CHECK":
            merchantPayee = "Check";
            break;

         default: // One-time online payments:

            // Skip over certain words at the beginning if they are present:
            if (payeeTokens[0].equalsIgnoreCase("BILL") && payeeTokens[1].equalsIgnoreCase("PAY")) {
               start = 2;
            } else if (payeeTokens[0].equalsIgnoreCase("PURCHASE") ||
                    payeeTokens[0].equalsIgnoreCase("REVERSAL")) {
               start = 1;
            } else {
               start = 0;
            }

            // Derive a payee from the remaining tokens:
            merchantPayee = makePayeeFromTokens(start);

            break;
      }
      System.out.println("Parsed merchant payee = " + merchantPayee);
      return merchantPayee;
   }

   public String makePayeeFromTokens(int start) {
      int i;
      StringBuilder merchantPayee;// Overwrite certain extraneous tokens like city and state so they won't be included in the merchant payee:
      cleanPayeeTokenList(start);

      // Skip over any numeric tokens:
      //noinspection StatementWithEmptyBody
      for (i = start; i < payeeTokens.length && payeeTokens[i].matches("^[0-9]*$"); i++) ;
      if (i < payeeTokens.length) {
         start = i;
      }

      // Always start with the first remaining token because it is always part of the merchant identification:
      merchantPayee = new StringBuilder(addCleanToken(payeeTokens[start++]));

      // Concatenate the following tokens until we find one that is not all characters or a short number (as in PIER 1):
      for (i = start;
           i < payeeTokens.length &&
                   payeeTokens[i].matches("^[A-Za-z'/*&,]*$|^[0-9]{1,3}$");
           i++) {
         merchantPayee.append(" ").append(addCleanToken(payeeTokens[i]));
      }
      return merchantPayee.toString();
   }

   // Remove any serial number, etc. from a token:
   private String addCleanToken(String payeeToken) {
      String[] tokens = payeeToken.split("\\*");
      String token;
      if (tokens.length == 0) {
         token = "";
      } else if (tokens.length == 1) {
         token = tokens[0];
      } else {
         token = (tokens[0].length() > 0) ? tokens[0] : tokens[1];
      }
      return token;
   }

   // Remove city, state and the word "RECURRING" from a token list:
   private void cleanPayeeTokenList(int start) {
      for (int i = start; i < payeeTokens.length; i++) {
         // Remove city followed by state from the merchant payee:
         if (payeeTokens[i].length() == 2 && states.indexOf(payeeTokens[i]) > 0) {
            payeeTokens[i] = "###";
            payeeTokens[i - 1] = "###";
         }

         // Remove the word "RECURRING" from the merchant payee:
         if (payeeTokens[i].equalsIgnoreCase("RECURRING")) {
            payeeTokens[i] = "###";
         }
      }
   }
}
