export interface DebtEdge {
  from: string;
  to: string;
  amount: number;
}

export function simplifyDebts(transactions: DebtEdge[]): DebtEdge[] {
  // Calculate net balances for each person
  const balances: Record<string, number> = {};

  for (const t of transactions) {
    if (!balances[t.from]) balances[t.from] = 0;
    if (!balances[t.to]) balances[t.to] = 0;

    balances[t.from] -= t.amount;
    balances[t.to] += t.amount;
  }

  // Separate into debtors (negative balance) and creditors (positive balance)
  const debtors: { person: string; amount: number }[] = [];
  const creditors: { person: string; amount: number }[] = [];

  for (const [person, balance] of Object.entries(balances)) {
    if (balance < -0.01) {
      debtors.push({ person, amount: -balance }); // amount owed
    } else if (balance > 0.01) {
      creditors.push({ person, amount: balance }); // amount to receive
    }
  }

  // Sort by amount descending (Greedy approach)
  debtors.sort((a, b) => b.amount - a.amount);
  creditors.sort((a, b) => b.amount - a.amount);

  const simplifiedEdges: DebtEdge[] = [];
  let d = 0; // debtor index
  let c = 0; // creditor index

  while (d < debtors.length && c < creditors.length) {
    const debtor = debtors[d];
    const creditor = creditors[c];

    const settledAmount = Math.min(debtor.amount, creditor.amount);

    simplifiedEdges.push({
      from: debtor.person,
      to: creditor.person,
      amount: Math.round(settledAmount * 100) / 100, // round to 2 decimals
    });

    debtor.amount -= settledAmount;
    creditor.amount -= settledAmount;

    if (debtor.amount < 0.01) d++;
    if (creditor.amount < 0.01) c++;
  }

  return simplifiedEdges;
}
