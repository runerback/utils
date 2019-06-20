using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Data;

namespace Runerback.Utils.Wpf.Converter
{
	sealed class AntiBooleanConverter : IValueConverter
	{
		object IValueConverter.Convert(object value, Type targetType, object parameter, CultureInfo culture)
		{
			return convert(value);
		}

		object IValueConverter.ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
		{
			return convert(value);
		}

		private bool convert(object value)
		{
			if (value != null && value is Boolean)
				return !(bool)value;
			return true;
		}
	}
}
