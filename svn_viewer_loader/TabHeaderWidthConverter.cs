using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace svn.viewer.loader;

[ValueConversion(typeof(double), typeof(double))]
public sealed class TabHeaderWidthConverter : DependencyObject, IValueConverter
{
    public int TabsCount
    {
        get => (int)GetValue(TabsCountProperty);
        set => SetValue(TabsCountProperty, value);
    }

    public static readonly DependencyProperty TabsCountProperty = DependencyProperty.Register(
        name: "TabsCount",
        propertyType: typeof(int),
        ownerType: typeof(TabHeaderWidthConverter),
        typeMetadata: new FrameworkPropertyMetadata(
            defaultValue: 1,
            flags: FrameworkPropertyMetadataOptions.AffectsRender));

    object IValueConverter.Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is double fullwidth && fullwidth > 0)
        {
            var tabCount = TabsCount;
            if (tabCount > 0)
            {
                return Math.Max(1, Math.Floor((fullwidth / tabCount)) - tabCount * 2);
            }
        }
        return 1d;
    }

    object IValueConverter.ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
    {
        throw new NotSupportedException("one-way");
    }
}