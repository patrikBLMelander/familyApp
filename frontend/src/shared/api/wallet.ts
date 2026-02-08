import { API_BASE_URL } from "../config";

export type WalletBalanceResponse = {
  id: string;
  memberId: string;
  balance: number;
};

export type ExpenseCategoryResponse = {
  id: string;
  name: string;
  emoji: string | null;
  isDefault: boolean;
};

export type SavingsGoalResponse = {
  id: string;
  memberId: string;
  name: string;
  targetAmount: number;
  currentAmount: number;
  emoji: string | null;
  isActive: boolean;
  isCompleted: boolean;
  isPurchased: boolean;
  completedAt: string | null;
  purchasedAt: string | null;
  purchaseTransactionId: string | null;
  progressPercentage: number;
  remainingAmount: number;
  createdAt: string;
  updatedAt: string;
};

export type WalletTransactionResponse = {
  id: string;
  walletId: string;
  amount: number;
  transactionType: string;
  description: string | null;
  categoryId: string | null;
  createdByMemberId: string | null;
  isDeleted: boolean;
  deletedAt: string | null;
  deletedByMemberId: string | null;
  createdAt: string;
};

export type WalletNotificationResponse = {
  id: string;
  memberId: string;
  transactionId: string;
  amount: number;
  description: string | null;
  shownAt: string | null;
  createdAt: string;
};

export type SavingsGoalAllocationRequest = {
  savingsGoalId: string;
  amount: number;
};

async function handleJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Request failed with status ${response.status}: ${errorText}`);
  }
  return (await response.json()) as T;
}

function getHeaders(): HeadersInit {
  const headers: HeadersInit = {
    "Content-Type": "application/json",
  };
  const deviceToken = localStorage.getItem("deviceToken");
  if (deviceToken) {
    headers["X-Device-Token"] = deviceToken;
  }
  return headers;
}

/**
 * Get wallet balance for current member
 */
export async function getWalletBalance(): Promise<WalletBalanceResponse> {
  const response = await fetch(`${API_BASE_URL}/wallet/balance`, {
    headers: getHeaders(),
  });
  return handleJson<WalletBalanceResponse>(response);
}

/**
 * Add allowance (give money) to a child
 */
export async function addAllowance(
  childMemberId: string,
  amount: number,
  description: string,
  savingsGoalAllocations?: SavingsGoalAllocationRequest[] | null
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/wallet/allowance`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({
      childMemberId,
      amount,
      description,
      savingsGoalAllocations: savingsGoalAllocations || null,
    }),
  });
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Failed to add allowance: ${errorText}`);
  }
}

/**
 * Allocate money from wallet balance to savings goals
 */
export async function allocateToSavingsGoals(
  allocations: SavingsGoalAllocationRequest[]
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/wallet/allocate-to-goals`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({
      savingsGoalAllocations: allocations,
    }),
  });
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Failed to allocate to goals: ${errorText}`);
  }
}

/**
 * Record expense (purchase)
 */
export async function recordExpense(
  amount: number,
  description: string | null,
  categoryId: string | null,
  savingsGoalAllocations: SavingsGoalAllocationRequest[] | null
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/wallet/expense`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({
      amount,
      description,
      categoryId,
      savingsGoalAllocations: savingsGoalAllocations || [],
    }),
  });
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Failed to record expense: ${errorText}`);
  }
}

/**
 * Get transaction history
 */
export async function getTransactionHistory(limit: number = 20): Promise<WalletTransactionResponse[]> {
  const response = await fetch(`${API_BASE_URL}/wallet/transactions?limit=${limit}`, {
    headers: getHeaders(),
  });
  return handleJson<WalletTransactionResponse[]>(response);
}

/**
 * Get unshown notifications
 */
export async function getUnshownNotifications(): Promise<WalletNotificationResponse[]> {
  const response = await fetch(`${API_BASE_URL}/wallet/notifications/unshown`, {
    headers: getHeaders(),
  });
  return handleJson<WalletNotificationResponse[]>(response);
}

/**
 * Mark notification as shown
 */
export async function markNotificationAsShown(notificationId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/wallet/notifications/${notificationId}/mark-shown`, {
    method: "POST",
    headers: getHeaders(),
  });
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Failed to mark notification as shown: ${errorText}`);
  }
}

/**
 * Get expense categories
 */
export async function getExpenseCategories(): Promise<ExpenseCategoryResponse[]> {
  const response = await fetch(`${API_BASE_URL}/wallet/categories`, {
    headers: getHeaders(),
  });
  return handleJson<ExpenseCategoryResponse[]>(response);
}

/**
 * Get savings goals for current member
 */
export async function getSavingsGoals(): Promise<SavingsGoalResponse[]> {
  const response = await fetch(`${API_BASE_URL}/wallet/savings-goals`, {
    headers: getHeaders(),
  });
  return handleJson<SavingsGoalResponse[]>(response);
}

/**
 * Get active savings goals for current member
 */
export async function getActiveSavingsGoals(): Promise<SavingsGoalResponse[]> {
  const response = await fetch(`${API_BASE_URL}/wallet/savings-goals/active`, {
    headers: getHeaders(),
  });
  return handleJson<SavingsGoalResponse[]>(response);
}

/**
 * Create savings goal
 */
export async function createSavingsGoal(
  name: string,
  targetAmount: number,
  emoji: string | null
): Promise<SavingsGoalResponse> {
  const response = await fetch(`${API_BASE_URL}/wallet/savings-goals`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({
      name,
      targetAmount,
      emoji: emoji || null,
    }),
  });
  return handleJson<SavingsGoalResponse>(response);
}

/**
 * Delete savings goal
 */
export async function deleteSavingsGoal(goalId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/wallet/savings-goals/${goalId}`, {
    method: "DELETE",
    headers: getHeaders(),
  });
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Failed to delete savings goal: ${errorText}`);
  }
}

/**
 * Mark savings goal as purchased
 */
export async function markGoalAsPurchased(
  goalId: string,
  purchaseTransactionId: string
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/wallet/savings-goals/${goalId}/mark-purchased`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify({
      purchaseTransactionId,
    }),
  });
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Failed to mark goal as purchased: ${errorText}`);
  }
}

/**
 * Get wallet balance for a specific member (for parents)
 */
export async function getMemberWalletBalance(memberId: string): Promise<WalletBalanceResponse> {
  const response = await fetch(`${API_BASE_URL}/wallet/members/${memberId}/balance`, {
    headers: getHeaders(),
  });
  return handleJson<WalletBalanceResponse>(response);
}

/**
 * Get active savings goals for a specific member (for parents)
 */
export async function getMemberActiveSavingsGoals(memberId: string): Promise<SavingsGoalResponse[]> {
  const response = await fetch(`${API_BASE_URL}/wallet/members/${memberId}/savings-goals/active`, {
    headers: getHeaders(),
  });
  return handleJson<SavingsGoalResponse[]>(response);
}

/**
 * Get transaction history for a specific member (for parents)
 */
export async function getMemberTransactionHistory(memberId: string, limit: number = 20): Promise<WalletTransactionResponse[]> {
  const response = await fetch(`${API_BASE_URL}/wallet/members/${memberId}/transactions?limit=${limit}`, {
    headers: getHeaders(),
  });
  return handleJson<WalletTransactionResponse[]>(response);
}
