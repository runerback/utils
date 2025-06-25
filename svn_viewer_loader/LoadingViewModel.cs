using System.ComponentModel;
using System.Windows;

namespace svn.viewer.loader;

public class LoadingViewModel : INotifyPropertyChanged
{
    private Size _viewSize;
    public Size ViewSize
    {
        get => _viewSize;
        set
        {
            if (value != _viewSize)
            {
                _viewSize = value;
                NotifyPropertyChanged(nameof(ViewSize));
                Resize();
            }
        }
    }

    private double _strokeThickness;
    public double StrokeThickness
    {
        get => _strokeThickness;
        private set
        {
            if (value != _strokeThickness)
            {
                _strokeThickness = value;
                NotifyPropertyChanged(nameof(StrokeThickness));
            }
        }
    }

    private Size _arcSize;
    public Size ArcSize
    {
        get => _arcSize;
        set
        {
            if (value != _arcSize)
            {
                _arcSize = value;
                NotifyPropertyChanged(nameof(ArcSize));
            }
        }
    }


    private Point _initPoint;
    public Point InitPoint
    {
        get => _initPoint;
        private set
        {
            if (value != _initPoint)
            {
                _initPoint = value;
                NotifyPropertyChanged(nameof(InitPoint));
            }
        }
    }

    #region Frames
    private Point _frame_00;
    public Point Frame_00
    {
        get => _frame_00;
        set
        {
            if (value != _frame_00)
            {
                _frame_00 = value;
                NotifyPropertyChanged(nameof(Frame_00));
            }
        }
    }

    private Point _frame_01;
    public Point Frame_01
    {
        get => _frame_01;
        set
        {
            if (value != _frame_01)
            {
                _frame_01 = value;
                NotifyPropertyChanged(nameof(Frame_01));
            }
        }
    }

    private Point _frame_02;
    public Point Frame_02
    {
        get => _frame_02;
        set
        {
            if (value != _frame_02)
            {
                _frame_02 = value;
                NotifyPropertyChanged(nameof(Frame_02));
            }
        }
    }

    private Point _frame_03;
    public Point Frame_03
    {
        get => _frame_03;
        set
        {
            if (value != _frame_03)
            {
                _frame_03 = value;
                NotifyPropertyChanged(nameof(Frame_03));
            }
        }
    }

    private Point _frame_04;
    public Point Frame_04
    {
        get => _frame_04;
        set
        {
            if (value != _frame_04)
            {
                _frame_04 = value;
                NotifyPropertyChanged(nameof(Frame_04));
            }
        }
    }

    private Point _frame_05;
    public Point Frame_05
    {
        get => _frame_05;
        set
        {
            if (value != _frame_05)
            {
                _frame_05 = value;
                NotifyPropertyChanged(nameof(Frame_05));
            }
        }
    }

    private Point _frame_06;
    public Point Frame_06
    {
        get => _frame_06;
        set
        {
            if (value != _frame_06)
            {
                _frame_06 = value;
                NotifyPropertyChanged(nameof(Frame_06));
            }
        }
    }

    private Point _frame_07;
    public Point Frame_07
    {
        get => _frame_07;
        set
        {
            if (value != _frame_07)
            {
                _frame_07 = value;
                NotifyPropertyChanged(nameof(Frame_07));
            }
        }
    }

    private Point _frame_08;
    public Point Frame_08
    {
        get => _frame_08;
        set
        {
            if (value != _frame_08)
            {
                _frame_08 = value;
                NotifyPropertyChanged(nameof(Frame_08));
            }
        }
    }

    private Point _frame_09;
    public Point Frame_09
    {
        get => _frame_09;
        set
        {
            if (value != _frame_09)
            {
                _frame_09 = value;
                NotifyPropertyChanged(nameof(Frame_09));
            }
        }
    }

    private Point _frame_10;
    public Point Frame_10
    {
        get => _frame_10;
        set
        {
            if (value != _frame_10)
            {
                _frame_10 = value;
                NotifyPropertyChanged(nameof(Frame_10));
            }
        }
    }

    private Point _frame_11;
    public Point Frame_11
    {
        get => _frame_11;
        set
        {
            if (value != _frame_11)
            {
                _frame_11 = value;
                NotifyPropertyChanged(nameof(Frame_11));
            }
        }
    }

    private Point _frame_12;
    public Point Frame_12
    {
        get => _frame_12;
        set
        {
            if (value != _frame_12)
            {
                _frame_12 = value;
                NotifyPropertyChanged(nameof(Frame_12));
            }
        }
    }

    private Point _frame_13;
    public Point Frame_13
    {
        get => _frame_13;
        set
        {
            if (value != _frame_13)
            {
                _frame_13 = value;
                NotifyPropertyChanged(nameof(Frame_13));
            }
        }
    }

    private Point _frame_14;
    public Point Frame_14
    {
        get => _frame_14;
        set
        {
            if (value != _frame_14)
            {
                _frame_14 = value;
                NotifyPropertyChanged(nameof(Frame_14));
            }
        }
    }

    private Point _frame_15;
    public Point Frame_15
    {
        get => _frame_15;
        set
        {
            if (value != _frame_15)
            {
                _frame_15 = value;
                NotifyPropertyChanged(nameof(Frame_15));
            }
        }
    }

    private Point _frame_16;
    public Point Frame_16
    {
        get => _frame_16;
        set
        {
            if (value != _frame_16)
            {
                _frame_16 = value;
                NotifyPropertyChanged(nameof(Frame_16));
            }
        }
    }

    private Point _frame_17;
    public Point Frame_17
    {
        get => _frame_17;
        set
        {
            if (value != _frame_17)
            {
                _frame_17 = value;
                NotifyPropertyChanged(nameof(Frame_17));
            }
        }
    }

    private Point _frame_18;
    public Point Frame_18
    {
        get => _frame_18;
        set
        {
            if (value != _frame_18)
            {
                _frame_18 = value;
                NotifyPropertyChanged(nameof(Frame_18));
            }
        }
    }

    private Point _frame_19;
    public Point Frame_19
    {
        get => _frame_19;
        set
        {
            if (value != _frame_19)
            {
                _frame_19 = value;
                NotifyPropertyChanged(nameof(Frame_19));
            }
        }
    }

    private Point _frame_20;
    public Point Frame_20
    {
        get => _frame_20;
        set
        {
            if (value != _frame_20)
            {
                _frame_20 = value;
                NotifyPropertyChanged(nameof(Frame_20));
            }
        }
    }

    private Point _frame_21;
    public Point Frame_21
    {
        get => _frame_21;
        set
        {
            if (value != _frame_21)
            {
                _frame_21 = value;
                NotifyPropertyChanged(nameof(Frame_21));
            }
        }
    }

    private Point _frame_22;
    public Point Frame_22
    {
        get => _frame_22;
        set
        {
            if (value != _frame_22)
            {
                _frame_22 = value;
                NotifyPropertyChanged(nameof(Frame_22));
            }
        }
    }

    private Point _frame_23;
    public Point Frame_23
    {
        get => _frame_23;
        set
        {
            if (value != _frame_23)
            {
                _frame_23 = value;
                NotifyPropertyChanged(nameof(Frame_23));
            }
        }
    }

    private Point _frame_24;
    public Point Frame_24
    {
        get => _frame_24;
        set
        {
            if (value != _frame_24)
            {
                _frame_24 = value;
                NotifyPropertyChanged(nameof(Frame_24));
            }
        }
    }

    private Point _frame_25;
    public Point Frame_25
    {
        get => _frame_25;
        set
        {
            if (value != _frame_25)
            {
                _frame_25 = value;
                NotifyPropertyChanged(nameof(Frame_25));
            }
        }
    }

    private Point _frame_26;
    public Point Frame_26
    {
        get => _frame_26;
        set
        {
            if (value != _frame_26)
            {
                _frame_26 = value;
                NotifyPropertyChanged(nameof(Frame_26));
            }
        }
    }

    private Point _frame_27;
    public Point Frame_27
    {
        get => _frame_27;
        set
        {
            if (value != _frame_27)
            {
                _frame_27 = value;
                NotifyPropertyChanged(nameof(Frame_27));
            }
        }
    }

    private Point _frame_28;
    public Point Frame_28
    {
        get => _frame_28;
        set
        {
            if (value != _frame_28)
            {
                _frame_28 = value;
                NotifyPropertyChanged(nameof(Frame_28));
            }
        }
    }

    private Point _frame_29;
    public Point Frame_29
    {
        get => _frame_29;
        set
        {
            if (value != _frame_29)
            {
                _frame_29 = value;
                NotifyPropertyChanged(nameof(Frame_29));
            }
        }
    }

    private Point _frame_30;
    public Point Frame_30
    {
        get => _frame_30;
        set
        {
            if (value != _frame_30)
            {
                _frame_30 = value;
                NotifyPropertyChanged(nameof(Frame_30));
            }
        }
    }

    private Point _frame_31;
    public Point Frame_31
    {
        get => _frame_31;
        set
        {
            if (value != _frame_31)
            {
                _frame_31 = value;
                NotifyPropertyChanged(nameof(Frame_31));
            }
        }
    }

    private Point _frame_32;
    public Point Frame_32
    {
        get => _frame_32;
        set
        {
            if (value != _frame_32)
            {
                _frame_32 = value;
                NotifyPropertyChanged(nameof(Frame_32));
            }
        }
    }

    private Point _frame_33;
    public Point Frame_33
    {
        get => _frame_33;
        set
        {
            if (value != _frame_33)
            {
                _frame_33 = value;
                NotifyPropertyChanged(nameof(Frame_33));
            }
        }
    }

    private Point _frame_34;
    public Point Frame_34
    {
        get => _frame_34;
        set
        {
            if (value != _frame_34)
            {
                _frame_34 = value;
                NotifyPropertyChanged(nameof(Frame_34));
            }
        }
    }

    private Point _frame_35;
    public Point Frame_35
    {
        get => _frame_35;
        set
        {
            if (value != _frame_35)
            {
                _frame_35 = value;
                NotifyPropertyChanged(nameof(Frame_35));
            }
        }
    }

    private Point _frame_36;
    public Point Frame_36
    {
        get => _frame_36;
        set
        {
            if (value != _frame_36)
            {
                _frame_36 = value;
                NotifyPropertyChanged(nameof(Frame_36));
            }
        }
    }

    private Point _frame_37;
    public Point Frame_37
    {
        get => _frame_37;
        set
        {
            if (value != _frame_37)
            {
                _frame_37 = value;
                NotifyPropertyChanged(nameof(Frame_37));
            }
        }
    }

    private Point _frame_38;
    public Point Frame_38
    {
        get => _frame_38;
        set
        {
            if (value != _frame_38)
            {
                _frame_38 = value;
                NotifyPropertyChanged(nameof(Frame_38));
            }
        }
    }

    private Point _frame_39;
    public Point Frame_39
    {
        get => _frame_39;
        set
        {
            if (value != _frame_39)
            {
                _frame_39 = value;
                NotifyPropertyChanged(nameof(Frame_39));
            }
        }
    }

    private Point _frame_40;
    public Point Frame_40
    {
        get => _frame_40;
        set
        {
            if (value != _frame_40)
            {
                _frame_40 = value;
                NotifyPropertyChanged(nameof(Frame_40));
            }
        }
    }

    private Point _frame_41;
    public Point Frame_41
    {
        get => _frame_41;
        set
        {
            if (value != _frame_41)
            {
                _frame_41 = value;
                NotifyPropertyChanged(nameof(Frame_41));
            }
        }
    }

    private Point _frame_42;
    public Point Frame_42
    {
        get => _frame_42;
        set
        {
            if (value != _frame_42)
            {
                _frame_42 = value;
                NotifyPropertyChanged(nameof(Frame_42));
            }
        }
    }

    private Point _frame_43;
    public Point Frame_43
    {
        get => _frame_43;
        set
        {
            if (value != _frame_43)
            {
                _frame_43 = value;
                NotifyPropertyChanged(nameof(Frame_43));
            }
        }
    }

    private Point _frame_44;
    public Point Frame_44
    {
        get => _frame_44;
        set
        {
            if (value != _frame_44)
            {
                _frame_44 = value;
                NotifyPropertyChanged(nameof(Frame_44));
            }
        }
    }

    private Point _frame_45;
    public Point Frame_45
    {
        get => _frame_45;
        set
        {
            if (value != _frame_45)
            {
                _frame_45 = value;
                NotifyPropertyChanged(nameof(Frame_45));
            }
        }
    }

    private Point _frame_46;
    public Point Frame_46
    {
        get => _frame_46;
        set
        {
            if (value != _frame_46)
            {
                _frame_46 = value;
                NotifyPropertyChanged(nameof(Frame_46));
            }
        }
    }

    private Point _frame_47;
    public Point Frame_47
    {
        get => _frame_47;
        set
        {
            if (value != _frame_47)
            {
                _frame_47 = value;
                NotifyPropertyChanged(nameof(Frame_47));
            }
        }
    }

    private Point _frame_48;
    public Point Frame_48
    {
        get => _frame_48;
        set
        {
            if (value != _frame_48)
            {
                _frame_48 = value;
                NotifyPropertyChanged(nameof(Frame_48));
            }
        }
    }

    private Point _frame_49;
    public Point Frame_49
    {
        get => _frame_49;
        set
        {
            if (value != _frame_49)
            {
                _frame_49 = value;
                NotifyPropertyChanged(nameof(Frame_49));
            }
        }
    }

    private Point _frame_50;
    public Point Frame_50
    {
        get => _frame_50;
        set
        {
            if (value != _frame_50)
            {
                _frame_50 = value;
                NotifyPropertyChanged(nameof(Frame_50));
            }
        }
    }

    private Point _frame_51;
    public Point Frame_51
    {
        get => _frame_51;
        set
        {
            if (value != _frame_51)
            {
                _frame_51 = value;
                NotifyPropertyChanged(nameof(Frame_51));
            }
        }
    }

    private Point _frame_52;
    public Point Frame_52
    {
        get => _frame_52;
        set
        {
            if (value != _frame_52)
            {
                _frame_52 = value;
                NotifyPropertyChanged(nameof(Frame_52));
            }
        }
    }

    private Point _frame_53;
    public Point Frame_53
    {
        get => _frame_53;
        set
        {
            if (value != _frame_53)
            {
                _frame_53 = value;
                NotifyPropertyChanged(nameof(Frame_53));
            }
        }
    }

    private Point _frame_54;
    public Point Frame_54
    {
        get => _frame_54;
        set
        {
            if (value != _frame_54)
            {
                _frame_54 = value;
                NotifyPropertyChanged(nameof(Frame_54));
            }
        }
    }

    private Point _frame_55;
    public Point Frame_55
    {
        get => _frame_55;
        set
        {
            if (value != _frame_55)
            {
                _frame_55 = value;
                NotifyPropertyChanged(nameof(Frame_55));
            }
        }
    }

    private Point _frame_56;
    public Point Frame_56
    {
        get => _frame_56;
        set
        {
            if (value != _frame_56)
            {
                _frame_56 = value;
                NotifyPropertyChanged(nameof(Frame_56));
            }
        }
    }

    private Point _frame_57;
    public Point Frame_57
    {
        get => _frame_57;
        set
        {
            if (value != _frame_57)
            {
                _frame_57 = value;
                NotifyPropertyChanged(nameof(Frame_57));
            }
        }
    }

    private Point _frame_58;
    public Point Frame_58
    {
        get => _frame_58;
        set
        {
            if (value != _frame_58)
            {
                _frame_58 = value;
                NotifyPropertyChanged(nameof(Frame_58));
            }
        }
    }

    private Point _frame_59;
    public Point Frame_59
    {
        get => _frame_59;
        set
        {
            if (value != _frame_59)
            {
                _frame_59 = value;
                NotifyPropertyChanged(nameof(Frame_59));
            }
        }
    }

    private Point _frame_60;
    public Point Frame_60
    {
        get => _frame_60;
        set
        {
            if (value != _frame_60)
            {
                _frame_60 = value;
                NotifyPropertyChanged(nameof(Frame_60));
            }
        }
    }

    private Point _frame_61;
    public Point Frame_61
    {
        get => _frame_61;
        set
        {
            if (value != _frame_61)
            {
                _frame_61 = value;
                NotifyPropertyChanged(nameof(Frame_61));
            }
        }
    }

    private Point _frame_62;
    public Point Frame_62
    {
        get => _frame_62;
        set
        {
            if (value != _frame_62)
            {
                _frame_62 = value;
                NotifyPropertyChanged(nameof(Frame_62));
            }
        }
    }

    private Point _frame_63;
    public Point Frame_63
    {
        get => _frame_63;
        set
        {
            if (value != _frame_63)
            {
                _frame_63 = value;
                NotifyPropertyChanged(nameof(Frame_63));
            }
        }
    }

    private Point _frame_64;
    public Point Frame_64
    {
        get => _frame_64;
        set
        {
            if (value != _frame_64)
            {
                _frame_64 = value;
                NotifyPropertyChanged(nameof(Frame_64));
            }
        }
    }

    private Point _frame_65;
    public Point Frame_65
    {
        get => _frame_65;
        set
        {
            if (value != _frame_65)
            {
                _frame_65 = value;
                NotifyPropertyChanged(nameof(Frame_65));
            }
        }
    }

    private Point _frame_66;
    public Point Frame_66
    {
        get => _frame_66;
        set
        {
            if (value != _frame_66)
            {
                _frame_66 = value;
                NotifyPropertyChanged(nameof(Frame_66));
            }
        }
    }

    private Point _frame_67;
    public Point Frame_67
    {
        get => _frame_67;
        set
        {
            if (value != _frame_67)
            {
                _frame_67 = value;
                NotifyPropertyChanged(nameof(Frame_67));
            }
        }
    }
    #endregion Frames

    private void Resize()
    {
        var (width, height) = (_viewSize.Width, _viewSize.Height);
        if (width == 0 || height == 0)
        {
            return;
        }
        StrokeThickness = 16d / 600 * width;
        ArcSize = new Size(271d / 600 * width, 271d / 600 * height);
        InitPoint = new Point(300d / 600 * width, 45d / 600 * height);
        #region Frames
        Frame_00 = new Point(300d / 600 * width, 045.00d / 600 * height);
        Frame_01 = new Point(315d / 600 * width, 045.44d / 600 * height);
        Frame_02 = new Point(330d / 600 * width, 046.77d / 600 * height);
        Frame_03 = new Point(345d / 600 * width, 049.00d / 600 * height);
        Frame_04 = new Point(360d / 600 * width, 052.16d / 600 * height);
        Frame_05 = new Point(375d / 600 * width, 056.28d / 600 * height);
        Frame_06 = new Point(390d / 600 * width, 061.41d / 600 * height);
        Frame_07 = new Point(405d / 600 * width, 067.62d / 600 * height);
        Frame_08 = new Point(420d / 600 * width, 075.00d / 600 * height);
        Frame_09 = new Point(435d / 600 * width, 083.67d / 600 * height);
        Frame_10 = new Point(450d / 600 * width, 093.78d / 600 * height);
        Frame_11 = new Point(465d / 600 * width, 105.58d / 600 * height);
        Frame_12 = new Point(480d / 600 * width, 119.38d / 600 * height);
        Frame_13 = new Point(495d / 600 * width, 135.68d / 600 * height);
        Frame_14 = new Point(510d / 600 * width, 155.35d / 600 * height);
        Frame_15 = new Point(525d / 600 * width, 180.00d / 600 * height);
        Frame_16 = new Point(540d / 600 * width, 213.83d / 600 * height);
        Frame_17 = new Point(555d / 600 * width, 300.00d / 600 * height);
        Frame_18 = new Point(540d / 600 * width, 386.17d / 600 * height);
        Frame_19 = new Point(525d / 600 * width, 420.00d / 600 * height);
        Frame_20 = new Point(510d / 600 * width, 444.65d / 600 * height);
        Frame_21 = new Point(495d / 600 * width, 464.32d / 600 * height);
        Frame_22 = new Point(480d / 600 * width, 480.62d / 600 * height);
        Frame_23 = new Point(465d / 600 * width, 494.42d / 600 * height);
        Frame_24 = new Point(450d / 600 * width, 506.22d / 600 * height);
        Frame_25 = new Point(435d / 600 * width, 516.33d / 600 * height);
        Frame_26 = new Point(420d / 600 * width, 525.00d / 600 * height);
        Frame_27 = new Point(405d / 600 * width, 532.38d / 600 * height);
        Frame_28 = new Point(390d / 600 * width, 538.59d / 600 * height);
        Frame_29 = new Point(375d / 600 * width, 543.72d / 600 * height);
        Frame_30 = new Point(360d / 600 * width, 547.84d / 600 * height);
        Frame_31 = new Point(345d / 600 * width, 551.00d / 600 * height);
        Frame_32 = new Point(330d / 600 * width, 553.23d / 600 * height);
        Frame_33 = new Point(315d / 600 * width, 554.56d / 600 * height);
        Frame_34 = new Point(300d / 600 * width, 555.00d / 600 * height);
        Frame_35 = new Point(285d / 600 * width, 554.56d / 600 * height);
        Frame_36 = new Point(270d / 600 * width, 553.23d / 600 * height);
        Frame_37 = new Point(255d / 600 * width, 551.00d / 600 * height);
        Frame_38 = new Point(240d / 600 * width, 547.84d / 600 * height);
        Frame_39 = new Point(225d / 600 * width, 543.72d / 600 * height);
        Frame_40 = new Point(210d / 600 * width, 538.59d / 600 * height);
        Frame_41 = new Point(195d / 600 * width, 532.38d / 600 * height);
        Frame_42 = new Point(180d / 600 * width, 525.00d / 600 * height);
        Frame_43 = new Point(165d / 600 * width, 516.33d / 600 * height);
        Frame_44 = new Point(150d / 600 * width, 506.22d / 600 * height);
        Frame_45 = new Point(135d / 600 * width, 494.42d / 600 * height);
        Frame_46 = new Point(120d / 600 * width, 480.62d / 600 * height);
        Frame_47 = new Point(105d / 600 * width, 464.32d / 600 * height);
        Frame_48 = new Point(090d / 600 * width, 444.65d / 600 * height);
        Frame_49 = new Point(075d / 600 * width, 420.00d / 600 * height);
        Frame_50 = new Point(060d / 600 * width, 386.17d / 600 * height);
        Frame_51 = new Point(045d / 600 * width, 300.00d / 600 * height);
        Frame_52 = new Point(060d / 600 * width, 213.83d / 600 * height);
        Frame_53 = new Point(075d / 600 * width, 180.00d / 600 * height);
        Frame_54 = new Point(090d / 600 * width, 155.35d / 600 * height);
        Frame_55 = new Point(105d / 600 * width, 135.68d / 600 * height);
        Frame_56 = new Point(120d / 600 * width, 119.38d / 600 * height);
        Frame_57 = new Point(135d / 600 * width, 105.58d / 600 * height);
        Frame_58 = new Point(150d / 600 * width, 093.78d / 600 * height);
        Frame_59 = new Point(165d / 600 * width, 083.67d / 600 * height);
        Frame_60 = new Point(180d / 600 * width, 075.00d / 600 * height);
        Frame_61 = new Point(195d / 600 * width, 067.62d / 600 * height);
        Frame_62 = new Point(210d / 600 * width, 061.41d / 600 * height);
        Frame_63 = new Point(225d / 600 * width, 056.28d / 600 * height);
        Frame_64 = new Point(240d / 600 * width, 052.16d / 600 * height);
        Frame_65 = new Point(255d / 600 * width, 049.00d / 600 * height);
        Frame_66 = new Point(270d / 600 * width, 046.77d / 600 * height);
        Frame_67 = new Point(285d / 600 * width, 045.44d / 600 * height);
        #endregion Frames
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    private void NotifyPropertyChanged(string name) => PropertyChanged?.Invoke(null, new(name));
}