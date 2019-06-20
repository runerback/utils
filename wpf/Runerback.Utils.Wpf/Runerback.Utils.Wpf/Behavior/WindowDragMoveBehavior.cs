using System.Windows;
using System.Windows.Input;
using System.Windows.Interactivity;

namespace Runerback.Utils.Wpf.Behavior
{
	public sealed class WindowDragMoveBehavior : Behavior<FrameworkElement>
	{
		private Cursor originCursor;
		private Window targetWindow;
		private bool bound = false;

		protected override void OnAttached()
		{
			var target = this.AssociatedObject;
			var targetWindow = Window.GetWindow(target);
			if (targetWindow == null) return;
			this.targetWindow = targetWindow;
			this.bound = true;

			this.originCursor = target.Cursor;
			target.Cursor = Cursors.SizeAll;
			target.PreviewMouseLeftButtonDown += onTargetPreviewMouseLeftButtonDown;
		}

		protected override void OnDetaching()
		{
			if (!this.bound) return;
			this.bound = false;

			var target = this.AssociatedObject;

			target.Cursor = this.originCursor;
			target.PreviewMouseLeftButtonDown -= onTargetPreviewMouseLeftButtonDown;

			this.targetWindow = null;
			this.originCursor = null;
		}

		private void onTargetPreviewMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
		{
			this.targetWindow.DragMove();
		}
	}
}
