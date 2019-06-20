using System;
using System.Windows;
using System.Windows.Data;

namespace Runerback.Utils.Wpf.Converter
{
	sealed class VisibleConverter : IValueConverter
	{
		object IValueConverter.Convert(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
		{
			if (value != null && value is bool)
				return (bool)value ? Visibility.Visible : Visibility.Collapsed;
			return Visibility.Hidden;
		}

		object IValueConverter.ConvertBack(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
		{
			if (value != null && value is Visibility)
				return (Visibility)value == Visibility.Visible;
			return false;
		}
	}
}
