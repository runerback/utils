using System;
using System.Windows.Data;

namespace Runerback.Utils.Wpf.Converter
{
	//when convert null to valuetype, return default value
	sealed class ValueTypeBindingConverter : IValueConverter
	{
		object IValueConverter.Convert(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
		{
			return SafeConvert(value, targetType);
		}

		object IValueConverter.ConvertBack(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
		{
			return SafeConvert(value, targetType);
		}

		private object SafeConvert(object value, Type targetType)
		{
			if (targetType.IsValueType && value == null)
				return Activator.CreateInstance(targetType);
			return value;
		}
	}
}
