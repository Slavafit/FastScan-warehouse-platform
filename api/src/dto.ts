import { Transform } from 'class-transformer';
import {
  IsBoolean,
  IsEmail,
  IsInt,
  IsNotEmpty,
  IsNumber,
  IsOptional,
  IsString,
  Length,
  MaxLength,
  MinLength,
} from 'class-validator';

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
  @IsString() @IsNotEmpty() reciever_guid!: string;
  @IsOptional() @IsString() user_guid?: string;
  @IsOptional() @IsInt() length?: number;
  @IsOptional() @IsInt() width?: number;
  @IsOptional() @IsInt() height?: number;
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
