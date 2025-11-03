package com.hixon.financialApp.model.merchant;

import com.hixon.financialApp.model.entity.DependentEntity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.register.RegisterException;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.NotImplementedException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class MerchantPayee extends DependentEntity {

    /*
     * Fields in the Wells Fargo download file transaction classifier:
     */
    private static final String insertQuery = "insert into merchant_payee (Merchant_idMerchant, payee) " +
            "values (";
    @Setter
    private UUID idMerchant;
    private String payee;

    public static void deleteByName(String merchantPayeeString) {
        // Delete any instance of the merchantPayeeString from the merchant_payee table:
        try {
            EntityInt.executeUpdate("delete from merchant_payee where payee = '" + merchantPayeeString + "'",
                    "deleting a merchant payee from the database.");
        } catch (EntityException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the payee and marks the entity as dirty.
     * Custom setter needed to track changes for save operations.
     */
    public void setPayee(String payee) {
        this.payee = payee;
        setDirty(true);
    }


    @Override
    public String getInsertQuery() {
        return insertQuery + "uuid_to_bin('" + idMerchant + "'), \"" + payee + "\")";
    }

    @Override
    public String getInsertOnDuplicateUpdateQuery() {
        // Throw the not implemented exception:
        throw new NotImplementedException(
                "The method getInsertOnDuplicateUpdateQuery() is not implemented for MerchantPayee.");
    }

    @Override
    public String getUpdateByIdQuery() {
        return null;
    }

    @Override
    public String getDeleteByIdQuery() {
        return null;
    }

    @Override
    public String getPrintableTypeName() {
        return getPrintableTypeName_static();
    }

    public static String getPrintableTypeName_static() {
        return "merchant payee";
    }

    public String toString  () {
        return "MerchantPayee: idMerchant = " + idMerchant + ", payee = " + payee;
    }


    /*
     * Constructors for MerchantPayee:
     */
    public MerchantPayee(String payee, UUID idMerchant) {
        super();
        this.idMerchant = idMerchant;
        this.payee = payee;
        super.setDirty(true);
    }

    /**
     * Validate the fields of an object.  Every entity is required to provide a method that validates the contents of
     * the entity.
     *
     * @return true if the object is valid
     */
    @Override
    public boolean isValid() {
        return true;
    }


    /*
     * Load and save methods for MerchantPayee:
     */
    public void save() throws RegisterException, EntityException {
        super.executeQueryForThis(insertQuery + "uuid_to_bin('" + idMerchant + "'), \"" + payee + "\")",
                "Databsae error occurred inserting MerchantPayee into the database.");
    }

    public static void deleteByMerchantAndPayee(Merchant merchant, String merchantPayeeString) throws EntityException, RegisterException {
        EntityInt.executeUpdate("delete from merchant_payee where Merchant_idMerchant = uuid_to_bin(\"" + merchant.getId() +
                "\") and payee = \"" + merchantPayeeString + "\"", "deleting a merchant payee from the database.");
    }


    /*
     * Main methods for MerchantPayee
     */
    // Get a list of payees for a merchant:
    public static List<MerchantPayee> getPayeesForMerchant(Merchant merchant) throws EntityException, SQLException {

        // Get a result set of payees for a merchant:
        ResultSet payeesRS = EntityInt.getRS("select * from merchant_payee where Merchant_idMerchant = uuid_to_bin('" +
                merchant.getId() + "')", "getting a list of payees for a merchant.");

        // Create a list of payees for the merchant:
        List<MerchantPayee> payees = new ArrayList<>();
        while (payeesRS.next()) {
            payees.add(new MerchantPayee(payeesRS.getString("payee"), merchant.getId()));
        }
        return payees;
    }
}
