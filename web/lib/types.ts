/** Ban sao kieu cua cac DTO do tang REST Java tra ve (api/dto/Dtos.java). */

export type ApiEnvelope<T> = {
  data?: T;
  meta?: PageMeta;
  error?: { code: string; message: string };
};

export type PageMeta = {
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

export type Money = number;

export type Order = {
  id: number;
  userId: number;
  showtimeId: number;
  ticketCode?: string;
  seatSubtotal: Money;
  comboSubtotal: Money;
  discountAmount: Money;
  totalAmount: Money;
  paymentStatus: "pending" | "paid" | "failed" | "refunded";
  orderStatus: "pending" | "confirmed" | "cancelled" | "redeemed";
  createdAt?: string;
};

/**
 * Giai doan vong doi cua phim, do backend tinh (FilmAvailabilityPolicy).
 *
 * Tang Next.js KHONG duoc tu suy ra tu ngay thang: quy tac phai dung chung mot noi voi
 * tang JSP va API, va moc thoi gian phai la ngay cua CSDL chu khong phai dong ho trinh
 * duyet. API public da loc bo EXPIRED/WITHDRAWN, hai gia tri do chi xuat hien o man hinh
 * quan tri.
 */
export type FilmAvailability =
  | "COMING"
  | "SHOWING"
  | "EXPIRING_SOON"
  | "EXPIRED"
  | "WITHDRAWN";

export type FilmSummary = {
  id: number;
  title: string;
  thumbnail?: string;
  banner?: string;
  rating?: number;
  ageRating?: string;
  durationMinutes?: number;
  releaseDate?: string;
  /** Ngay chieu cuoi cung (bao gom ca ngay do). Vang mat = chua gioi han. */
  endDate?: string;
  status?: string;
  availability?: FilmAvailability;
  /** So ngay con lai toi endDate; am nghia la da qua han. */
  daysUntilEnd?: number;
  format?: string;
  country?: string;
};

export type Film = FilmSummary & {
  otherTitles?: string;
  actors?: string;
  directors?: string;
  categories?: string[];
  trailerUrl?: string;
  language?: string;
  subtitles?: string;
  description?: string;
};

export type Cinema = {
  id: number;
  cityId: number;
  cityName?: string;
  name: string;
  address?: string;
  phone?: string;
  status?: string;
  avatar?: string;
  bannerUrl?: string;
  description?: string;
  roomCount?: number;
};

export type City = { id: number; name: string };

export type Showtime = {
  id: number;
  filmId: number;
  filmTitle?: string;
  filmThumbnail?: string;
  ageRating?: string;
  cinemaId: number;
  cinemaName?: string;
  cityId: number;
  roomId: number;
  roomName?: string;
  startTime: string;
  endTime: string;
  basePrice: number;
  format?: string;
  version?: string;
  language?: string;
  formatVersionDisplay?: string;
};

export type BookingEligibility = {
  showtimeId: number;
  eligible: boolean;
  code:
    | "AVAILABLE"
    | "CUTOFF_REACHED"
    | "ROOM_INACTIVE"
    | "FILM_ENDED"
    | "FILM_EXPIRED"
    | "BEFORE_RELEASE"
    | "INVALID_SHOWTIME";
  message: string;
};

export type Seat = {
  id: number;
  seatId: number;
  seatKey: string;
  rowLabel: string;
  seatNumber: number;
  seatType: "standard" | "vip" | "couple";
  status: "available" | "held" | "booked";
  extraFee: number;
  selectable: boolean;
  heldUntil?: string;
};

export type Combo = {
  id: number;
  name: string;
  image?: string;
  price: number;
  description?: string;
};

export type Comment = {
  id: number;
  filmId: number;
  userFullName?: string;
  rate: number;
  content?: string;
  createdAt?: string;
};

export type User = {
  id: number;
  username?: string;
  fullName?: string;
  email: string;
  phone?: string;
  address?: string;
  avatar?: string;
  role: "member" | "manager" | "admin";
  loyaltyPoints: number;
  totalSpent?: number;
  membershipTier: string;
  tierDisplayName: string;
};

export type FilmDetail = {
  film: Film;
  showtimes: Showtime[];
  comments: Comment[];
};

export type HeaderData = {
  nowShowing: FilmSummary[];
  upcoming: FilmSummary[];
  cinemas: Cinema[];
};
