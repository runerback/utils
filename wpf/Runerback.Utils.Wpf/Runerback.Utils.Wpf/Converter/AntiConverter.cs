using System;
using System.Windows;
using System.Windows.Data;

namespace Runerback.Utils.Wpf.Converter
{
	sealed class AntiConverter : IValueConverter
	{
		object IValueConverter.Convert(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
		{
			return getOppositeValue(value);
		}

		object IValueConverter.ConvertBack(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
		{
			return getOppositeValue(value);
		}

		private object getOppositeValue(object value)
		{
			if (value == null)
				return null;

			Type type = value.GetType();

			//handle primitive type
			switch (Type.GetTypeCode(type))
			{
				case TypeCode.Boolean:
					return !(bool)value;
				case TypeCode.Byte:
					return byte.MaxValue - (byte)value;
				case TypeCode.Char:
					return char.MaxValue - (char)value;
				case TypeCode.DateTime:
					return DateTime.MaxValue - (DateTime)value;
				case TypeCode.Decimal:
					return -(decimal)value;
				case TypeCode.Double:
					return -(double)value;
				case TypeCode.Int16:
					return -(short)value;
				case TypeCode.Int32:
					return -(int)value;
				case TypeCode.Int64:
					return -(long)value;
				case TypeCode.SByte:
					return sbyte.MaxValue - (sbyte)value;
				case TypeCode.Single:
					return -(float)value;
				case TypeCode.UInt16:
					return ushort.MaxValue - (ushort)value;
				case TypeCode.UInt32:
					return uint.MaxValue - (uint)value;
				case TypeCode.UInt64:
					return ulong.MaxValue - (ulong)value;
				default: break;
			}

			//handle other type
			if (type == typeof(Visibility))
				return (Visibility)value == Visibility.Visible ? Visibility.Collapsed : Visibility.Visible;
			else if(type == typeof(HorizontalAlignment))
				switch ((HorizontalAlignment)value)
				{
					case HorizontalAlignment.Left: return HorizontalAlignment.Right;
					case HorizontalAlignment.Right: return HorizontalAlignment.Left;
					case HorizontalAlignment.Center: return HorizontalAlignment.Stretch;
					case HorizontalAlignment.Stretch: return HorizontalAlignment.Center;
					default: break;
				}
			else if(type == typeof(VerticalAlignment))
				switch ((VerticalAlignment)value)
				{
					case VerticalAlignment.Top: return VerticalAlignment.Bottom;
					case VerticalAlignment.Bottom: return VerticalAlignment.Top;
					case VerticalAlignment.Center: return VerticalAlignment.Stretch;
					case VerticalAlignment.Stretch: return VerticalAlignment.Center;
					default: break;
				}

			return value;
		}
	}
}
