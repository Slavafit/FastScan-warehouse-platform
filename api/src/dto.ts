import { Transform } from 'class-transformer';
import {
  IsBoolean,
  IsEmail,
  IsEnum,
  IsInt,
  IsNotEmpty,
  IsNumber,
  IsOptional,
  IsString,
  Length,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';
import { DictionaryType, Role } from './generated/prisma/client.js';

export class LoginDto {
  @IsString() @IsNotEmpty() login!: string;
  @IsString() @IsNotEmpty() password!: string;
}
export class RefreshDto {
  @IsString() @IsNotEmpty() refresh_token!: string;
}
export class ClientRegistrationDto {
  @IsString() @IsNotEmpty() client_code!: string;
  @IsEmail() email!: string;
  @IsString() @MinLength(8) @MaxLength(100) password!: string;
}
export class ReceiverUpsertDto {
  @IsOptional() @IsString() client_code?: string;
  @IsString() @IsNotEmpty() first_name!: string;
  @IsString() @IsNotEmpty() last_name!: string;
  @IsOptional() @IsInt() package_shoes = 0;
  @IsOptional() @IsInt() bag_package = 0;
}
export class SaveOrderDto {
  @IsString() @IsNotEmpty() client_code!: string;
  @IsOptional() @IsString() client?: string;
  @IsString() @IsNotEmpty() tracking_number!: string;
  @IsOptional() @IsNumber() gross_weight?: number;
  @IsOptional() with_box?: unknown;
  @IsOptional() @IsString() reciever_guid?: string;
  @IsOptional() @IsString() receiver_guid?: string;
  @IsOptional() @IsString() user_guid?: string;
  @IsOptional() @IsInt() length?: number;
  @IsOptional() @IsInt() width?: number;
  @IsOptional() @IsInt() height?: number;
  @IsOptional() @IsString() composition_guid?: string;
  @IsOptional() @IsString() @MaxLength(50) composition_code?: string;
  @IsOptional() @IsString() @MaxLength(500) description?: string;
  @IsOptional() @IsNumber() @Min(0) declared_value?: number;
  @IsOptional() @IsString() @Length(3, 3) currency?: string;
}
export class BoxDto {
  @IsOptional() @IsString() guid?: string;
  @IsOptional() @IsString() codeid?: string;
  @IsOptional() @IsString() tracking_code?: string;
  @IsOptional() @IsInt() width?: number;
  @IsOptional() @IsInt() height?: number;
  @IsOptional() @IsInt() length?: number;
  @IsOptional() @IsNumber() v_weight?: number;
  @IsOptional() @IsNumber() gross_weight?: number;
  @IsOptional() @IsString() partial?: string;
}
export class BoxOrderDto {
  @IsString() @IsNotEmpty() order!: string;
  @IsString() @IsNotEmpty() box!: string;
  @IsOptional() @IsString() desc?: string;
}
export class MobileBoxOrderDto {
  @IsString() @IsNotEmpty() order_guid!: string;
  @IsOptional() @IsString() description?: string;
}
export class MobileBatchBoxDto {
  @IsString() @IsNotEmpty() box_guid!: string;
}
export class GuidDto {
  @IsString() @IsNotEmpty() guid!: string;
}
export class PartialDto {
  @IsOptional() @IsString() guid?: string;
  @IsString() @IsNotEmpty() partial_code!: string;
  @IsString() @IsNotEmpty() partial_name!: string;
  @IsOptional() @IsInt() status?: number;
}
export class SendPartialDto {
  @IsString() @IsNotEmpty() partial!: string;
}
export class PlatformSettingsDto {
  @IsString() @Length(1, 12) prefix!: string;
  @IsString() @IsNotEmpty() @MaxLength(120) warehouse!: string;
  @IsString() @IsNotEmpty() @MaxLength(120) country!: string;
}
export class ProfileDto {
  @IsString() @IsNotEmpty() first_name!: string;
  @IsString() @IsNotEmpty() last_name!: string;
  @IsOptional() @IsString() @MaxLength(40) phone?: string;
  @IsOptional() @IsString() @MaxLength(500) address?: string;
  @IsBoolean() email_notifications!: boolean;
  @IsBoolean() push_notifications!: boolean;
}
export class ClientOrderCreateDto {
  @IsString() @IsNotEmpty() tracking_number!: string;
  @IsOptional() @IsString() composition_guid?: string;
  @IsOptional() @IsString() @MaxLength(500) description?: string;
  @IsOptional() @IsNumber() @Min(0) declared_value?: number;
  @IsOptional() @IsString() @Length(3, 3) currency?: string;
}
export class NotificationDto {
  @IsOptional() @IsString() client_code?: string;
  @IsOptional() @IsString() type = 'INFO';
  @IsString() @IsNotEmpty() title!: string;
  @IsString() @IsNotEmpty() @MaxLength(1000) message!: string;
}
export class BoolQueryDto {
  @IsOptional()
  @Transform(({ value }) => value === true || value === 'true')
  @IsBoolean()
  unreadOnly = false;
}

export class StaffUserDto {
  @IsString() @IsNotEmpty() login!: string;
  @IsString() @MinLength(8) @MaxLength(100) password!: string;
  @IsEnum(Role) role!: Role;
}

export class StaffUserStatusDto {
  @IsBoolean() enabled!: boolean;
}

export class OrderCompositionDto {
  @IsString() @IsNotEmpty() @MaxLength(50) code!: string;
  @IsString() @IsNotEmpty() @MaxLength(120) name!: string;
  @IsOptional() @IsInt() sort_order = 0;
}

export class DictionaryItemDto {
  @IsEnum(DictionaryType) type!: DictionaryType;
  @IsString() @IsNotEmpty() @MaxLength(50) code!: string;
  @IsString() @IsNotEmpty() @MaxLength(120) name!: string;
  @IsOptional() @IsInt() sort_order = 0;
}

export class StaffClientUpdateDto {
  @IsOptional() @IsString() @IsNotEmpty() @MaxLength(120) first_name?: string;
  @IsOptional() @IsString() @IsNotEmpty() @MaxLength(120) last_name?: string;
  @IsOptional() @IsEmail() email?: string;
  @IsOptional() @IsString() @MaxLength(40) phone?: string;
  @IsOptional() @IsString() @MaxLength(500) address?: string;
  @IsOptional() @IsInt() package_shoes?: number;
  @IsOptional() @IsInt() bag_package?: number;
}

export class StaffClientCreateDto {
  @IsOptional() @IsString() @MaxLength(50) client_code?: string;
  @IsString() @IsNotEmpty() @MaxLength(120) first_name!: string;
  @IsString() @IsNotEmpty() @MaxLength(120) last_name!: string;
  @IsOptional() @IsEmail() email?: string;
  @IsOptional() @IsString() @MinLength(8) @MaxLength(100) password?: string;
  @IsOptional() @IsString() @MaxLength(40) phone?: string;
  @IsOptional() @IsString() @MaxLength(500) address?: string;
  @IsOptional() @IsInt() package_shoes = 0;
  @IsOptional() @IsInt() bag_package = 0;
}

export class PasswordResetDto {
  @IsString() @MinLength(8) @MaxLength(100) password!: string;
}

export class ManifestCreateDto {
  @IsString() @IsNotEmpty() partial_guid!: string;
}
