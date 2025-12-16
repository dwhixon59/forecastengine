# Barclays QFX Sample Merchant Names
## For Test Data Creation

This file contains actual merchant names from the sample QFX file (qdl20251215.qfx) to use when creating test cases.

## Transaction Categories

### Payments (CREDIT)
- `PAYMENT RECV'D CHECKFREE`
- `Payment Received`

### Rewards/Credits (CREDIT)
- `FLIGHT CENTS`
- `AA 25% INFLIGHT CREDIT`
- `AA WIFI CREDIT`
- `AMAZON MKTPLACE PMTS` (refund/return)

### Fees (DEBIT)
- `PRIMARY ANNUAL FEE`
- `OVERDRAFT FEE FOR` (not in sample, but likely exists)

### Interest Charges (DEBIT)
- `INTEREST CHARGE-PURCHASES`
- `INTEREST CHARGE-PURCHASE` (singular version also seen)

### Subscription Services (DEBIT)
- `NETFLIX.COM`
- `NETFLIX, INC.`
- `Spotify P3CA3D8014` (with transaction ID)
- `Spotify P3B812A56F`
- `Spotify P398EB2A04`
- `Spotify USA`
- `GOOGLE *YouTube TV`
- `GOOGLE *TV`
- `GOOGLE *Fandango at Ho`
- `GOOGLE *YouTube Videos`
- `Visible` (mobile service)
- `VISIBLE` (all caps version)

### Utilities (DEBIT)
- `PEACE RIVER ELECTRIC`
- `Spectrum Mobile`
- `Spectrum`
- `ADT SECURITY*320925392`
- `MANATEECOUNTYUTIL`

### Fitness (DEBIT)
- `LA FITNESS`
- `LA Fitness  *AnnualFee`

### Loans/Insurance (DEBIT)
- `MORI Loan Re`
- `STATE FARM  INSURANCE` (note double space)

### Online Services (DEBIT)
- `AMAZON MKTPL*H87MO7QJ3` (Amazon Marketplace with order ID)
- `AMAZON MKTPL*761GT8S03`
- `AMAZON MKTPL*6241O71L3`
- `AMAZON MKTPL*236DF9MM3`
- `AMAZON MKTPL*WY06F94E3`
- `AMAZON MKTPL*Z78QI6XQ1`
- `AMAZON MKTPL*ZG4HS69S1`
- `AMAZON MKTPL*ZC17341G2`
- `Amazon web services`
- `WP*JETPACK UBKKUTL1X` (WordPress Jetpack)
- `VXNBILL.COM`
- `GENMEDIA*`
- `ECHST.NET 866-452-5108`

### Retail (DEBIT)
- `PUBLIX #1553` (store number)
- `7-ELEVEN 38991` (store number)
- `WAWA 5185` (store number)
- `CRACKER BARREL #73 BRA`

### Restaurants (DEBIT)
- `UEP*GINZA`
- `CHICK-FIL-A #03238`
- `STARBUCKS AEROPUERTO J` (airport location)

### Travel - Airlines (DEBIT)
- `icelandairCB59ZC` (booking code)
- `AMERICAN  0014472768827` (booking number)
- `AA WIFI 1-888-649-6711`
- `AA INFLIGHT`
- `BRITISH A 1252203142408` (British Airways + booking)
- `BRITISH A 1254243027959`
- `BA Inflight Sales`

### Travel - Hotels (DEBIT)
- `LOS SUENOS MARRIOTT OC`
- `MARRIOTT CRYSTAL SHORE`
- `MVCI Holidays France`

### Travel - Transportation (DEBIT)
- `JUMBO CAR` (car rental)
- `Cars on Booking`
- `RAIL EUROPE *RE-8E3MHU`
- `Trainline`
- `SHELL OIL 57543868400`
- `EXXON DG-C STORE INC`
- `GASOLINERA EL COYOL` (Costa Rica gas station)
- `GLOBAL VIA RUTA 27` (Costa Rica toll)
- `ERACTOLL 6HYV77` (toll)

### Travel - Activities (DEBIT)
- `COSTA RICA DIVE Y SURF`
- `ATLANTIS AQUAVENTURE`
- `VIATORTRIPADVISOR UK` (tour booking)
- `DELTA LOS SUENOS` (restaurant/bar)
- `4/7 ATO SJO` (likely activity/transfer)

### Services (DEBIT)
- `PY *PRODIGY PEST SOLU` (pest control)
- `Prodigy Pest Solutions`
- `TRUGREEN    *LOCKBOX` (lawn care)
- `TAMPA INT L AIRPOR` (parking)
- `DOWNTOWN SARASOTA PARK` (parking)

### Other (DEBIT)
- `CROWDER BROS - LAKEWOO` (feed/farm supply)
- `Wikimedia` (donation)
- `BOUNCE - USEBOUNCE.COM` (luggage storage)

## Test Data Selection

### Recommended Test Transactions

**Simple Purchase** (easy parsing):
```
TRNTYPE: DEBIT
DTPOSTED: 20251210050000.000
TRNAMT: -28.20
FITID: 554328650712053126673293001
NAME: NETFLIX.COM
```

**Complex Purchase** (needs cleaning):
```
TRNTYPE: DEBIT
DTPOSTED: 20251202050000.000
TRNAMT: -119.40
FITID: 823050953365000490226473001
NAME: WP*JETPACK UBKKUTL1X
```

**Payment**:
```
TRNTYPE: CREDIT
DTPOSTED: 20251119050000.000
TRNAMT: 2219.00
FITID: 75140215323111925069629108
NAME: PAYMENT RECV'D CHECKFREE
```

**Annual Fee**:
```
TRNTYPE: DEBIT
DTPOSTED: 20251130050000.000
TRNAMT: -99.00
FITID: 00005000020251130
NAME: PRIMARY ANNUAL FEE
```

**Interest Charge**:
```
TRNTYPE: DEBIT
DTPOSTED: 20251210050000.000
TRNAMT: -237.23
FITID: 00005000020251210
NAME: INTEREST CHARGE-PURCHASES
```

**Reward Credit**:
```
TRNTYPE: CREDIT
DTPOSTED: 20251005040000.000
TRNAMT: 5.00
FITID: 751402152780000000005727239
NAME: AA 25% INFLIGHT CREDIT
```

**Retail with Store Number**:
```
TRNTYPE: DEBIT
DTPOSTED: 20250326040000.000
TRNAMT: -38.95
FITID: 023053750850006367786823001
NAME: PUBLIX #1553
```

**Subscription with Transaction ID**:
```
TRNTYPE: DEBIT
DTPOSTED: 20251123050000.000
TRNAMT: -21.97
FITID: 027034053251562440087983001
NAME: Spotify P3CA3D8014
```

