# Financial Institution Database Reference

## Register Table - `financialInstitution` Field

The `register` table has a `financialInstitution` VARCHAR(256) field that determines which financial institution implementation to use for transaction imports.

### Supported Values

| Database Value | Java Implementation | Import Format | Notes |
|----------------|---------------------|---------------|-------|
| `Wells Fargo Bank` | `WellsFargoBank` | CSV | Recommended value for Wells Fargo accounts |
| `WellsFargo` | `WellsFargoBank` | CSV | Alternative (case-insensitive) |
| `Wells Fargo` | `WellsFargoBank` | CSV | Alternative (case-insensitive) |
| `Barclays Bank` | `BarclaysBank` | QFX | Requires QFX filename via `createBarclays()` |
| `Barclays` | `BarclaysBank` | QFX | Alternative (case-insensitive) |
| `Bank` | `GenericBank` | Manual | Generic fallback for unknown banks |
| `Generic Bank` | `GenericBank` | Manual | Alternative (case-insensitive) |
| `Generic` | `GenericBank` | Manual | Alternative (case-insensitive) |

### Database Schema

```sql
-- The register table should have this field:
ALTER TABLE register 
ADD COLUMN financialInstitution VARCHAR(256) NULL;

-- Example updates for existing registers:
UPDATE register SET financialInstitution = 'Wells Fargo Bank' WHERE name LIKE '%Wells Fargo%';
UPDATE register SET financialInstitution = 'Barclays Bank' WHERE name LIKE '%Barclays%' OR name LIKE '%Aviator%';
UPDATE register SET financialInstitution = 'Bank' WHERE financialInstitution IS NULL;
```

### How It Works

1. **SessionController** calls `FinancialInstitutionFactory.create(sessionController)`
2. **Factory** reads `register.getFinancialInstitution()` from the database
3. **Switch expression** matches the value (case-insensitive) to create the appropriate implementation
4. **Institution object** is returned and used for transaction imports

### Adding New Institutions

To add support for a new financial institution (e.g., Chase Bank):

1. **Create the implementation class** (e.g., `ChaseBank.java`) extending `FinancialInstitution`
2. **Add to the switch expression** in `FinancialInstitutionFactory.create()`:
   ```java
   case "chase bank", "chase", "jpmorgan chase" ->
       new ChaseBank(register, budget, forecast, view, notificationService);
   ```
3. **Update this documentation** with the new supported value
4. **Update database** with the new institution name:
   ```sql
   UPDATE register SET financialInstitution = 'Chase Bank' WHERE name LIKE '%Chase%';
   ```

### Error Handling

- **NULL or Empty**: Throws `IllegalArgumentException` - "Register does not have a financial institution specified"
- **Unknown Value**: Throws `IllegalArgumentException` - "Unknown financial institution: '{value}'. Supported institutions: ..."
- **Barclays without QFX**: Throws `UnsupportedOperationException` - "Barclays Bank requires QFX file import..."

### Migration Notes

If you have existing registers without the `financialInstitution` field set:

1. Run a query to identify registers without the field:
   ```sql
   SELECT id, name, accountNumber 
   FROM register 
   WHERE financialInstitution IS NULL OR financialInstitution = '';
   ```

2. Update based on patterns:
   ```sql
   -- Wells Fargo registers
   UPDATE register 
   SET financialInstitution = 'Wells Fargo Bank' 
   WHERE (name LIKE '%Wells Fargo%' OR name LIKE '%WF%') 
     AND (financialInstitution IS NULL OR financialInstitution = '');
   
   -- Barclays/Aviator registers  
   UPDATE register 
   SET financialInstitution = 'Barclays Bank'
   WHERE (name LIKE '%Barclays%' OR name LIKE '%Aviator%')
     AND (financialInstitution IS NULL OR financialInstitution = '');
   
   -- Generic fallback for everything else
   UPDATE register 
   SET financialInstitution = 'Bank'
   WHERE financialInstitution IS NULL OR financialInstitution = '';
   ```

### Best Practices

- ✅ **Use consistent naming**: Stick to the canonical names ("Wells Fargo Bank", "Barclays Bank", "Bank")
- ✅ **Set during register creation**: Include financialInstitution when creating new registers
- ✅ **Validate in UI**: Provide a dropdown of supported institutions when creating/editing registers
- ✅ **Case-insensitive matching**: The factory handles case variations automatically
- ⚠️ **QFX special case**: Barclays requires the QFX filename, so cannot use auto-creation

### Example Register Records

```sql
-- Wells Fargo checking account
INSERT INTO register (id, name, accountNumber, financialInstitution, ...) 
VALUES (UUID(), 'Wells Fargo Checking', '1234567890', 'Wells Fargo Bank', ...);

-- Generic savings account
INSERT INTO register (id, name, accountNumber, financialInstitution, ...) 
VALUES (UUID(), 'Local Credit Union Savings', '0987654321', 'Bank', ...);

-- Barclays credit card (note: requires special handling for QFX import)
INSERT INTO register (id, name, accountNumber, financialInstitution, ...) 
VALUES (UUID(), 'Aviator Mastercard', '1111222233334444', 'Barclays Bank', ...);
```

---

**Last Updated**: December 21, 2025  
**Version**: 1.0  
**Related Classes**: 
- `FinancialInstitutionFactory`
- `WellsFargoBank`
- `BarclaysBank`
- `GenericBank`
- `Register`

