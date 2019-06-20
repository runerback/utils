using System;
using System.Windows;
using System.Windows.Data;

namespace Runerback.Utils.Wpf.Converter
{
	sealed class AntiVisibleConverter : IValueConverter
	{
		object IValueConverter.Convert(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
		{
			if (value != null && value is bool && (bool)value)
				return Visibility.Collapsed;
			return Visibility.Visible;
		}

		object IValueConverter.ConvertBack(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
		{
			if (value != null && value is Visibility && (Visibility)value == Visibility.Visible)
				return false;
			return true;
		}
	}
}
