using System.Windows;
using System.Windows.Controls.Primitives;
using System.Windows.Interactivity;

namespace Runerback.Utils.Wpf.Behavior
{
	public sealed class CloseWindowBehavior : Behavior<ButtonBase>
	{
		private Window targetWindow;
		private bool bound = false;

		protected override void OnAttached()
		{
			var target = this.AssociatedObject;
			var targetWindow = Window.GetWindow(target);
			if (targetWindow == null) return;
			this.targetWindow = targetWindow;
			this.bound = true;

			target.Click += OnClicked;
		}

		protected override void OnDetaching()
		{
			if (!this.bound) return;
			this.bound = false;

			this.AssociatedObject.Click -= OnClicked;
			this.targetWindow = null;
		}

		private void OnClicked(object sender, RoutedEventArgs e)
		{
			this.targetWindow.Close();
		}
	}
}
