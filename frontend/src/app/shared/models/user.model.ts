export interface UserResponse {
  id: number;
  email: string;
  fullName: string;
  avatarUrl: string | null;
  active: boolean;
  roles: string[];
}

export interface UpdateProfileRequest {
  fullName: string;
  avatarUrl: string | null;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface CreateUserRequest {
  email: string;
  fullName: string;
  password: string;
  roleNames: string[];
}