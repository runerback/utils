using System.Windows;
using System.Windows.Controls;

namespace Runerback.Utils.Wpf.Controls
{
	public sealed class WindowHeaderBar : ContentControl
	{
		public WindowHeaderBar()
		{

		}

		public bool ShowButton
		{
			get { return (bool)this.GetValue(ShowButtonProperty); }
			set { this.SetValue(ShowButtonProperty, value); }
		}

		public static readonly DependencyProperty ShowButtonProperty =
			DependencyProperty.Register(
				"ShowButton",
				typeof(bool),
				typeof(WindowHeaderBar),
				new PropertyMetadata(true));
	}
}
