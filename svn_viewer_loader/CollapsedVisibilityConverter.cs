using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace svn.viewer.loader;

[ValueConversion(typeof(bool), typeof(Visibility))]
public sealed class CollapsedVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is bool booleanValue && booleanValue)
        {
            return Visibility.Collapsed;
        }
        return Visibility.Visible;
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
    {
        return value is Visibility visibility && visibility == Visibility.Collapsed;
    }
}