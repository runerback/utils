using System.Globalization;
using System.Windows.Data;

namespace svn.viewer.loader;

[ValueConversion(typeof(string), typeof(Uri))]
public sealed class UriConverter : IValueConverter
{
    object IValueConverter.Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is string { Length: > 0 } url &&
            Uri.TryCreate(url, UriKind.Absolute, out var uri))
        {
            return uri;
        }
        return default!;
    }

    object IValueConverter.ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
    {
        throw new NotSupportedException("one-way only");
    }
}