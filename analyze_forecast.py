#!/usr/bin/env python3
import csv
from datetime import datetime
from decimal import Decimal

csv_file = r'C:\Users\dwhix\Projects\forecastengine\LongTermForecast-BillPayAccount-DaveForecast.csv'

# Container for transactions (July 1, 2026 to June 30, 2027)
transactions = []
month_data = {}
current_month = None
balance_history = []

# Parse CSV
with open(csv_file, 'r', encoding='utf-8') as f:
    lines = f.readlines()

in_july_2026_or_later = False
in_target_period = False
target_end_reached = False

for line_num, line in enumerate(lines, 1):
    line = line.strip()
    if not line:
        continue

    # Detect month headers
    if ' - ' in line and any(month in line for month in ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']):
        parts = line.split(',')
        current_month = parts[0]
        print(f"Month: {current_month}")
        continue

    # Skip header rows
    if 'Date,Category' in line:
        continue

    # Parse transaction rows
    parts = [p.strip() for p in line.split(',')]
    if len(parts) < 7:
        continue

    try:
        date_str = parts[0]
        category = parts[1]
        payee = parts[2]
        memo = parts[3]
        credit_str = parts[4].replace('$', '').replace(',', '').replace('"', '')
        debit_str = parts[5].replace('$', '').replace(',', '').replace('"', '')
        balance_str = parts[6].replace('$', '').replace(',', '').replace('"', '')

        # Skip if not a valid transaction
        if not credit_str and not debit_str:
            continue

        # Start collecting from July 2026
        if 'July - 2026' in current_month:
            in_july_2026_or_later = True

        if in_july_2026_or_later and not target_end_reached:
            in_target_period = True

            credit = Decimal(credit_str) if credit_str else Decimal(0)
            debit = Decimal(debit_str) if debit_str else Decimal(0)
            balance = Decimal(balance_str.replace('-', ''))

            # Handle negative balance
            if '-' in balance_str:
                balance = -balance

            transactions.append({
                'month': current_month,
                'date': date_str,
                'category': category,
                'payee': payee,
                'credit': credit,
                'debit': debit,
                'balance': balance
            })

            balance_history.append((current_month, date_str, balance))

            # Stop at end of June 2027
            if 'June - 2027' in current_month and date_str and date_str.isdigit():
                if int(date_str) >= 30:
                    target_end_reached = True

    except (ValueError, IndexError) as e:
        pass

print(f"\nTotal transactions in period: {len(transactions)}")
print(f"Period: July 1, 2026 to June 30, 2027\n")

# Calculate totals
total_income = sum(t['credit'] for t in transactions)
total_expense = sum(t['debit'] for t in transactions)
net_change = total_income - total_expense

# Get starting and ending balances
if transactions:
    starting_balance = transactions[0]['balance']
    ending_balance = transactions[-1]['balance']

    print("=== VERIFICATION OF FORECAST SUMMARY ===\n")
    print(f"First transaction (starting balance): {starting_balance}")
    print(f"Last transaction (ending balance): {ending_balance}")
    print(f"Expected net change: {net_change}")
    print(f"Calculated from balance change: {ending_balance - starting_balance}")
    print(f"Difference: {abs((ending_balance - starting_balance) - net_change)}")
    print()

    print(f"Total Income (Credits): ${total_income:,.2f}")
    print(f"Total Expenses (Debits): ${total_expense:,.2f}")
    print(f"Net Change: ${net_change:,.2f}")
    print(f"Average Monthly Depletion: ${net_change / 12:,.2f}")
    print()

    # Find min and max balances
    all_balances = [t['balance'] for t in transactions]
    max_balance = max(all_balances)
    min_balance = min(all_balances)

    max_date = next((t['date'] for t in transactions if t['balance'] == max_balance), None)
    min_date = next((t['date'] for t in transactions if t['balance'] == min_balance), None)

    print(f"Highest Balance: ${max_balance:,.2f} on {max_date}")
    print(f"Lowest Balance: ${min_balance:,.2f} on {min_date}")

    # Find first negative balance
    first_negative = next((t for t in transactions if t['balance'] < 0), None)
    if first_negative:
        print(f"First Negative Balance: ${first_negative['balance']:,.2f} on {first_negative['date']}")
    else:
        print("No negative balances found")
    print()

    print("=== SUMMARY CLAIMS ===")
    print("Starting balance: $1,048")
    print("Ending balance: $-1,352")
    print("Net change: $-2,401")
    print("Average depletion: $-200")
    print("Highest balance: $6,354 on 07-01-2026")
    print("Lowest balance: $-1,561 on 06-14-2027")
    print("First negative: $-33 on 10-09-2026")
    print("Total income: $113,297")
    print("Total expense: $-115,698")
    print()

    print("=== DISCREPANCIES ===")
    if starting_balance != Decimal('1048'):
        print(f"❌ Starting balance mismatch: Expected $1,048, Got ${starting_balance}")
    if ending_balance != Decimal('-1352'):
        print(f"❌ Ending balance mismatch: Expected $-1,352, Got ${ending_balance}")
    if net_change != Decimal('-2401'):
        print(f"❌ Net change mismatch: Expected $-2,401, Got ${net_change}")
    if total_income != Decimal('113297'):
        print(f"❌ Total income mismatch: Expected $113,297, Got ${total_income}")
    if total_expense != Decimal('115698'):
        print(f"❌ Total expense mismatch: Expected $115,698, Got ${-total_expense}")

