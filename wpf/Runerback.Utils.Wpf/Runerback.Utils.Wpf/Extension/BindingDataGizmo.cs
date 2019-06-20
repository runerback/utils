using System.Windows;

namespace Runerback.Utils.Wpf
{
	public sealed class BindingDataGizmo : Freezable
	{
		protected override Freezable CreateInstanceCore()
		{
			return new BindingDataGizmo();
		}

		public object Data
		{
			get { return (object)this.GetValue(DataProperty); }
			set { this.SetValue(DataProperty, value); }
		}

		public static readonly DependencyProperty DataProperty =
			DependencyProperty.Register(
				"Data",
				typeof(object),
				typeof(BindingDataGizmo));
	}
}
