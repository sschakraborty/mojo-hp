create database if not exists mojo_hp_database;
use mojo_hp_database;

create table if not exists Accounts (
	id integer unsigned primary key auto_increment,
    name varchar(150) not null,
    email varchar(150) not null,
    roll varchar(50) not null,
    phone_no varchar(50) not null,
    pwd varchar(100) not null
) engine = InnoDB;

create table if not exists Problems (
	code varchar(25) primary key,
    name varchar(150) not null,
    question text not null,
    tags varchar(250)
) engine = InnoDB;

create table if not exists Testcases (
	Problems_code varchar(25) not null,
    in_path varchar(250) not null,
    out_path varchar(250) not null,
    tl integer unsigned not null default 1000,
    constraint fk_code foreign key (Problems_code) references Problems (code)
    on update cascade on delete cascade
) engine = InnoDB;

create table if not exists Solve_log (
    log_id varchar(50) primary key not null,
	log_t timestamp not null default current_timestamp,
	Accounts_id integer unsigned not null,
    Problems_code varchar(25) not null,
    code text not null,
    language varchar(10) not null,
    status varchar(25) not null,
    constraint fk_code_2 foreign key (Problems_code) references Problems (code)
    on update cascade on delete cascade,
    constraint fk_Accounts_id foreign key (Accounts_id) references Accounts (id)
    on update cascade on delete cascade
) engine = InnoDB;

create table if not exists Comments (
    id integer unsigned primary key auto_increment,
    Problems_code varchar(25) not null,
    Accounts_id integer unsigned not null,
    comment varchar(1024) not null,
    c_time timestamp not null default current_timestamp,
    constraint fk_code_3 foreign key (Problems_code) references Problems (code)
    on update cascade on delete cascade,
    constraint fk_Accounts_id_2 foreign key (Accounts_id) references Accounts (id)
    on update cascade on delete cascade
) engine = InnoDB;