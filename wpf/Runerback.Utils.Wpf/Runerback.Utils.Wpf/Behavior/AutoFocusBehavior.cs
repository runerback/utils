using System.Windows;
using System.Windows.Interactivity;

namespace Runerback.Utils.Wpf.Behavior
{
	public sealed class AutoFocusBehavior : Behavior<FrameworkElement>
	{
		protected override void OnAttached()
		{
			var target = this.AssociatedObject;

			Focus(target);

			target.IsVisibleChanged += onTargetIsVisibleChanged;
		}

		protected override void OnDetaching()
		{
			this.AssociatedObject.IsVisibleChanged += onTargetIsVisibleChanged;
		}

		private void onTargetIsVisibleChanged(object sender, DependencyPropertyChangedEventArgs e)
		{
			if ((bool)e.NewValue)
				Focus((FrameworkElement)sender);
		}

		private void Focus(FrameworkElement target)
		{
			target.BringIntoView();
			target.Focus();
		}
	}
}
