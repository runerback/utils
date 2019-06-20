using System.ComponentModel;

namespace Runerback.Utils.Wpf
{
	public class ViewModelBase : INotifyPropertyChanged
	{
		protected ViewModelBase() { }

		private PropertyChangedEventHandler propertyChangedHandler;
		event PropertyChangedEventHandler INotifyPropertyChanged.PropertyChanged
		{
			add { this.propertyChangedHandler += value; }
			remove { this.propertyChangedHandler -= value; }
		}

		protected void NotifyPropertyChanged(string propertyName)
		{
			if (propertyChangedHandler != null)
				propertyChangedHandler(this, new PropertyChangedEventArgs(propertyName));
		}
	}
}
