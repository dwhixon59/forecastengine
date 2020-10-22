package com.hixon.financialApp.model.user;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class User extends IndependentEntity {


   /*
    * Fields:
    */
   //
   String userName;
   String password;
   String firstName;
   String lastName;
   String email;
   String phoneNumber;
   Calendar updatedTimestamp;


   /*
    * Getters and Setters:
    */
   public String getUserName() {
      return userName;
   }
   public void setUserName(String userName) {
      this.userName = userName;
   }

   public String getPassword() {
      return password;
   }
   public void setPassword(String password) {
      this.password = password;
   }

   public String getFirstName() {
      return firstName;
   }
   public void setFirstName(String firstName) {
      this.firstName = firstName;
   }

   public String getLastName() {
      return lastName;
   }
   public void setLastName(String lastName) {
      this.lastName = lastName;
   }

   public String getEmail() {
      return email;
   }
   public void setEmail(String email) {
      this.email = email;
   }

   public String getPhoneNumber() {
      return phoneNumber;
   }
   public void setPhoneNumber(String phoneNumber) {
      this.phoneNumber = phoneNumber;
   }

   public Calendar getUpdatedTimestamp() {
      return updatedTimestamp;
   }
   public void setUpdatedTimestamp(Calendar updatedTimestamp) {
      this.updatedTimestamp = updatedTimestamp;
   }

   /*
    * Constructors:
    */
   // Empty constructor:
   public User(boolean createId) {
      super(createId);
   }

   // Full constructors (with and without ID:
   public User(String userName, String password, String firstName, String lastName, String email, String phoneNumber,
               Calendar updatedTimestamp) {
      super(true);
      this.userName = userName;
      this.password = password;
      this.firstName = firstName;
      this.lastName = lastName;
      this.email = email;
      this.phoneNumber = phoneNumber;
      this.updatedTimestamp = (Calendar) updatedTimestamp.clone();
   }
   public User(UUID id, String userName, String password, String firstName, String lastName, String email,
               String phoneNumber, Calendar updatedTimestamp) {
      super(false);
      this.id = id;
      this.userName = userName;
      this.password = password;
      this.firstName = firstName;
      this.lastName = lastName;
      this.email = email;
      this.phoneNumber = phoneNumber;
      this.updatedTimestamp = (Calendar) updatedTimestamp.clone();
   }

   // Constructor from a ResultSet:
   public User(ResultSet rs) throws SQLException {
      super(false);
      this.id = UUID.fromString(rs.getString("u.id"));
      this.userName = rs.getString("u.userName");
      this.password = rs.getString("u.password");
      this.firstName = rs.getString("u.firstName");
      this.lastName = rs.getString("u.lastName");
      this.email = rs.getString("u.email");
      this.phoneNumber = rs.getString("u.phoneNumber");
      this.updatedTimestamp = Utility.SqlTimestampToCalendarDate(rs.getTimestamp("u.updatedTimestamp"));
   }


   /*
    * Helper methods:
    */
   @Override
   public String toString() {
      return "User{" +
              "userName='" + userName + '\'' +
              ", password='" + password + '\'' +
              ", firstName='" + firstName + '\'' +
              ", lastName='" + lastName + '\'' +
              ", email='" + email + '\'' +
              ", phoneNumber='" + phoneNumber + '\'' +
              ", updatedTimestamp ='" + Utility.calendarDateToStringDate(updatedTimestamp) + '\'' +
              ", id=" + id +
              '}';
   }
   
   
   /*
    * CRUD methods:
    */
   // The select query:
   public static final String selectColumns = " bin_to_uuid(u.idUser) as 'u.id', u.userName as 'u.userName', u.password " +
           "as 'u.password', u.firstName as 'u.firstName', u.lastName as 'u.lastName', u.email as 'u.email', " +
           "u.phoneNumber as 'u.phoneNumber', u.updatedTimeStamp as 'u.updatedTimeStamp'";
   public static String getSelectColumns() {
      return selectColumns;
   }
   public static final String selectQuery = "select" + getSelectColumns() + " from user u" ;
   public static String getSelectQuery() {
      return selectQuery;
   }

   // The insert query:
   public static final String insertQuery = "insert into user (idUser, userName, password, firstName, " +
           "lastName, email, phoneNumber) values (";
   @Override
   public String getInsertQuery() {
      return insertQuery + "uuid_to_bin('" + id + "'), " + userName + ", " + password + ", " + firstName + ", " +
              lastName + ", " + email + ", " + phoneNumber + "')";
   }

   // The update query:
   public static final String updateQuery = "update user set ";
   public String getupdateClause() {
      return  "userName = " + userName + ", password = " + password + ", firstName = " + firstName + ", lastName = "
              + lastName + ", email = " + email + ", phoneNumber = " + phoneNumber;
   }
   @Override
   public String getUpdateByIdQuery() throws BudgetException {
      return updateQuery + getupdateClause() + " where idUser = uuid_to_bin('" + id + "')";
   }

   // The insert on duplicate update query:
   @Override
   public String getInsertOnDuplicateUpdateQuery() {
      return getInsertQuery() + "on duplicate key update "+ getupdateClause();
   }
   
   // The delete query:
   public static final String deleteQuery = "delete from user ";
   public static String getDeleteQuery() {return deleteQuery;}
   @Override
   public String getDeleteByIdQuery() {
      return getDeleteQuery() + " where idUser = uuid_to_bin('" + id + "')";
   }

   // The entity name:
   @Override
   public String getPrintableEntityTypeName() {
      return "user";
   }


   /*
    * Main methods:
    */
   // Get a list of the users:
   public static List<User> getAllUsers() throws EntityException, SQLException {

      ResultSet rs = EntityInt.getRS(User.getSelectQuery(), "attempting to retrieve a list of users");
      List<User> users = new ArrayList<>();
      int i = 1;
      while (rs.next()) {
         users.add(new User(rs));
      }
      return users;
   }

}
