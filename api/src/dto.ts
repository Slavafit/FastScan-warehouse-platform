import { Transform } from 'class-transformer';
import {
  IsBoolean,
  IsEmail,
  IsEnum,
  IsInt,
  IsIn,
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
  @IsOptional() @IsString() @MaxLength(50) client_code?: string;
  @IsString() @IsNotEmpty() @MaxLength(120) first_name!: string;
  @IsString() @IsNotEmpty() @MaxLength(120) last_name!: string;
  @IsOptional() @IsInt() @IsIn([0, 1]) package_shoes = 0;
  @IsOptional() @IsInt() @IsIn([0, 1]) bag_package = 0;
}
export class SaveOrderDto {
  @IsString() @IsNotEmpty() @MaxLength(50) client_code!: string;
  @IsOptional() @IsString() @MaxLength(240) client?: string;
  @IsString() @IsNotEmpty() @MaxLength(160) tracking_number!: string;
  @IsOptional() @IsNumber() @Min(0) gross_weight?: number;
  @IsOptional() with_box?: unknown;
  @IsOptional() @IsString() reciever_guid?: string;
  @IsOptional() @IsString() receiver_guid?: string;
  @IsOptional() @IsString() user_guid?: string;
  @IsOptional() @IsInt() @Min(0) length?: number;
  @IsOptional() @IsInt() @Min(0) width?: number;
  @IsOptional() @IsInt() @Min(0) height?: number;
  @IsOptional() @IsString() composition_guid?: string;
  @IsOptional() @IsString() @MaxLength(50) composition_code?: string;
  @IsOptional() @IsString() @MaxLength(500) description?: string;
  @IsOptional() @IsNumber() @Min(0) declared_value?: number;
  @IsOptional() @IsString() @Length(3, 3) currency?: string;
}
export class BoxDto {
  @IsOptional() @IsString() guid?: string;
  @IsOptional() @IsString() @MaxLength(50) codeid?: string;
  @IsOptional() @IsString() @MaxLength(160) tracking_code?: string;
  @IsOptional() @IsInt() @Min(0) width?: number;
  @IsOptional() @IsInt() @Min(0) height?: number;
  @IsOptional() @IsInt() @Min(0) length?: number;
  @IsOptional() @IsNumber() @Min(0) v_weight?: number;
  @IsOptional() @IsNumber() @Min(0) gross_weight?: number;
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
  @IsString() @IsNotEmpty() @MaxLength(100) partial_code!: string;
  @IsString() @IsNotEmpty() @MaxLength(160) partial_name!: string;
  @IsOptional() @IsInt() @Min(0) status?: number;
}
export class SendPartialDto {
  @IsString() @IsNotEmpty() partial!: string;
}
export class PlatformSettingsDto {
  @IsString() @Length(1, 12) prefix!: string;
  @IsString() @IsNotEmpty() @MaxLength(120) warehouse!: string;
  @IsString() @IsNotEmpty() @MaxLength(120) country!: string;
  @IsInt() @Min(1000) volumetric_divisor = 6000;
}
export class PasswordResetConfirmDto {
  @IsString() @IsNotEmpty() token!: string;
  @IsString() @MinLength(8) @MaxLength(100) password!: string;
}

export class TelegramClientAccessDto {
  @IsString() @IsNotEmpty() telegram_id!: string;
  @IsString() @IsNotEmpty() chat_id!: string;
  @IsOptional() @IsString() @MaxLength(64) username?: string;
  @IsOptional() @IsString() @MaxLength(120) first_name?: string;
  @IsOptional() @IsString() @MaxLength(120) last_name?: string;
}

export class TelegramTokenDto {
  @IsString() @IsNotEmpty() token!: string;
}
export class ProfileDto {
  @IsString() @IsNotEmpty() first_name!: string;
  @IsString() @IsNotEmpty() last_name!: string;
  @IsOptional() @IsString() @MaxLength(40) phone?: string;
  @IsOptional() @IsString() @MaxLength(500) address?: string;
  @IsBoolean() email_notifications!: boolean;
  @IsBoolean() push_notifications!: boolean;
  @IsIn(['INTERNAL', 'EMAIL', 'TELEGRAM', 'WHATSAPP'])
  notification_channel!: 'INTERNAL' | 'EMAIL' | 'TELEGRAM' | 'WHATSAPP';
  @IsOptional() @IsString() @MaxLength(40) whatsapp_phone?: string;
  @IsOptional() @IsInt() @IsIn([0, 1]) package_shoes?: number;
  @IsOptional() @IsInt() @IsIn([0, 1]) bag_package?: number;
}
export class ClientOrderCreateDto {
  @IsString() @IsNotEmpty() @MaxLength(160) tracking_number!: string;
  @IsOptional() @IsString() composition_guid?: string;
  @IsOptional() @IsString() @MaxLength(500) description?: string;
  @IsOptional() @IsNumber() @Min(0) declared_value?: number;
  @IsOptional() @IsString() @Length(3, 3) currency?: string;
  @IsOptional() @IsBoolean() with_box?: boolean;
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
  @IsOptional() @IsInt() @IsIn([0, 1]) package_shoes?: number;
  @IsOptional() @IsInt() @IsIn([0, 1]) bag_package?: number;
}

export class StaffClientCreateDto {
  @IsOptional() @IsString() @MaxLength(50) client_code?: string;
  @IsString() @IsNotEmpty() @MaxLength(120) first_name!: string;
  @IsString() @IsNotEmpty() @MaxLength(120) last_name!: string;
  @IsOptional() @IsEmail() email?: string;
  @IsOptional() @IsString() @MinLength(8) @MaxLength(100) password?: string;
  @IsOptional() @IsString() @MaxLength(40) phone?: string;
  @IsOptional() @IsString() @MaxLength(500) address?: string;
  @IsOptional() @IsInt() @IsIn([0, 1]) package_shoes = 0;
  @IsOptional() @IsInt() @IsIn([0, 1]) bag_package = 0;
}

export class PasswordResetDto {
  @IsString() @MinLength(8) @MaxLength(100) password!: string;
}

export class ManifestCreateDto {
  @IsString() @IsNotEmpty() partial_guid!: string;
}
